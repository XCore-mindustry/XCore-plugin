package org.xcore.plugin.chat;

import arc.util.Strings;
import io.avaje.inject.PreDestroy;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import mindustry.gen.Player;
import org.xcore.plugin.concurrent.Async;
import org.xcore.plugin.concurrent.ScheduledTasks;
import org.xcore.plugin.config.TomlXcoreConfig;
import org.xcore.plugin.config.TomlXcoreConfig.TranslationDeliveryConfig;
import org.xcore.plugin.service.ChatFormatService;
import org.xcore.plugin.service.ClientCompatibilityService;
import org.xcore.plugin.service.TranslationMetricsService;
import org.xcore.plugin.session.Session;
import org.xcore.plugin.session.SessionService;
import org.xcore.plugin.translation.ChatTranslation;
import org.xcore.plugin.translation.ChatTranslationService;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;
import java.util.function.Predicate;

/**
 * Decides who sees a chat line, in which form and when. Recipients with the translator off
 * get the original at once; the others are grouped by translator language and wait for that
 * group's translation according to {@code translation.delivery}:
 *
 * <ul>
 *   <li>{@code deadline}: the translated line if it is ready within {@code deadline_millis},
 *       otherwise the original at the deadline and the translation as a follow-up line if it
 *       arrives within {@code late_translation_millis};</li>
 *   <li>{@code original-first}: the original at once, the translation as a follow-up line;</li>
 *   <li>{@code wait}: the translated line, or the original after {@code late_translation_millis}.</li>
 * </ul>
 *
 * <p>Each language group is a lane: lines leave it in the order the messages were sent, so a
 * cached reply cannot overtake the message it answers. A line waits for earlier ones at most
 * until its own deadline. A failed translation always delivers the original.
 *
 * <p>Game thread only. Translation futures complete elsewhere and hop back via {@link Async}.
 */
@Singleton
public class ChatDeliveryService {

    private final TomlXcoreConfig config;
    private final SessionService sessionService;
    private final ChatFormatService chatFormatService;
    private final ClientCompatibilityService clientCompatibilityService;
    private final ChatTranslationService translations;
    private final TranslationMetricsService metrics;
    private final Async async;
    private final ScheduledTasks scheduledTasks;
    private final DeliveryScheduler scheduler;
    private final LongSupplier nanoTime;
    private final Map<String, ArrayDeque<PendingLine>> lanes = new HashMap<>();

    /** Runs a task on the game thread after a delay. */
    @FunctionalInterface
    interface DeliveryScheduler {
        void after(long delayMillis, Runnable task);
    }

    @Inject
    public ChatDeliveryService(TomlXcoreConfig config,
                               SessionService sessionService,
                               ChatFormatService chatFormatService,
                               ClientCompatibilityService clientCompatibilityService,
                               ChatTranslationService translations,
                               TranslationMetricsService metrics,
                               Async async) {
        this(config, sessionService, chatFormatService, clientCompatibilityService, translations, metrics, async,
                new ScheduledTasks(ChatDeliveryService.class), null, System::nanoTime);
    }

    ChatDeliveryService(TomlXcoreConfig config,
                        SessionService sessionService,
                        ChatFormatService chatFormatService,
                        ClientCompatibilityService clientCompatibilityService,
                        ChatTranslationService translations,
                        TranslationMetricsService metrics,
                        Async async,
                        DeliveryScheduler scheduler,
                        LongSupplier nanoTime) {
        this(config, sessionService, chatFormatService, clientCompatibilityService, translations, metrics, async,
                null, scheduler, nanoTime);
    }

    private ChatDeliveryService(TomlXcoreConfig config,
                                SessionService sessionService,
                                ChatFormatService chatFormatService,
                                ClientCompatibilityService clientCompatibilityService,
                                ChatTranslationService translations,
                                TranslationMetricsService metrics,
                                Async async,
                                ScheduledTasks scheduledTasks,
                                DeliveryScheduler scheduler,
                                LongSupplier nanoTime) {
        this.config = config;
        this.sessionService = sessionService;
        this.chatFormatService = chatFormatService;
        this.clientCompatibilityService = clientCompatibilityService;
        this.translations = translations;
        this.metrics = metrics;
        this.async = async;
        this.scheduledTasks = scheduledTasks;
        this.scheduler = scheduler != null
                ? scheduler
                : (delayMillis, task) -> scheduledTasks.after(delayMillis / 1000f, task);
        this.nanoTime = nanoTime;
    }

    @PreDestroy
    void shutdown() {
        if (scheduledTasks != null) {
            scheduledTasks.close();
        }
    }

    /** Global chat from a local player. The author has already seen their own line. */
    public void deliverGlobal(Player author, String text) {
        String formatted = chatFormatService.formatChat(author, text);
        List<Recipient> recipients = new ArrayList<>();
        for (Session session : sessionService.getAllCachedSnapshot()) {
            Player player = session.player;
            if (session.data == null || player == null || player == author) continue;
            recipients.add(new Recipient(session, formatted, true));
        }

        deliver(new ChatLine(author, author.plainName(), author.uuid(), text), recipients);
    }

    /**
     * Team chat from a local player, including the author's own line. Only clients that are
     * likely Foo's get the unformatted text; vanilla shows it as a chat bubble.
     */
    public void deliverTeam(Player author, String text) {
        List<Recipient> recipients = new ArrayList<>();
        for (Session session : sessionService.findByTeam(author.team())) {
            if (session.data == null || session.player == null) continue;
            String formatted = chatFormatService.formatTeamChat(author, session.locale(), text);
            boolean passRaw = clientCompatibilityService.isLikelyFoosClient(session.player);
            recipients.add(new Recipient(session, formatted, passRaw, session.player == author));
        }

        deliver(new ChatLine(author, author.plainName(), author.uuid(), text), recipients);
    }

    /**
     * Chat that arrived from another server or Discord, rendered per recipient with the
     * {@code bundleKey} template. {@code authorKey} identifies the author for the translation
     * budget.
     */
    public void deliverRemote(String authorKey,
                              String authorName,
                              String text,
                              String bundleKey,
                              Map<String, Object> args,
                              Predicate<Session> filter) {
        List<Recipient> recipients = new ArrayList<>();
        for (Session session : sessionService.getAllCachedSnapshot()) {
            if (session.data == null || session.player == null) continue;
            if (filter != null && !filter.test(session)) continue;
            recipients.add(new Recipient(session, session.locale().format(bundleKey, args), false));
        }

        deliver(new ChatLine(null, Strings.stripColors(authorName), authorKey, text), recipients);
    }

    private void deliver(ChatLine line, List<Recipient> recipients) {
        Map<String, List<Recipient>> byLanguage = new LinkedHashMap<>();
        for (Recipient recipient : recipients) {
            String language = recipient.session.data.translatorLanguage;
            if (recipient.self || language == null || language.equals("off")) {
                sendOriginal(line, recipient);
            } else if (!translations.supports(language)) {
                metrics.incrementGlobal("unsupported_language_total");
                sendOriginal(line, recipient);
            } else {
                byLanguage.computeIfAbsent(language, ignored -> new ArrayList<>()).add(recipient);
            }
        }

        if (byLanguage.isEmpty()) {
            return;
        }

        Map<String, CompletableFuture<ChatTranslation>> futures =
                translations.translate(line.authorKey(), line.text(), byLanguage.keySet());
        long now = nanoTime.getAsLong();
        long deadlineMillis = deadlineMillis();
        for (var entry : byLanguage.entrySet()) {
            String language = entry.getKey();
            CompletableFuture<ChatTranslation> translation = futures.get(language);
            ArrayDeque<PendingLine> lane = lanes.computeIfAbsent(language, ignored -> new ArrayDeque<>());
            if (lane.isEmpty() && translation.isDone()) {
                sendResolved(line, entry.getValue(), translation.join());
                continue;
            }

            lane.addLast(new PendingLine(line, entry.getValue(), translation, now,
                    now + TimeUnit.MILLISECONDS.toNanos(deadlineMillis)));
            translation.whenComplete((ignored, error) -> async.main(() -> drain(language)));
            if (deadlineMillis > 0) {
                scheduler.after(deadlineMillis, () -> drain(language));
            } else {
                drain(language);
            }
        }
    }

    private void drain(String language) {
        ArrayDeque<PendingLine> lane = lanes.get(language);
        if (lane == null) {
            return;
        }

        long now = nanoTime.getAsLong();
        while (!lane.isEmpty()) {
            PendingLine head = lane.peekFirst();
            if (head.translation.isDone()) {
                lane.pollFirst();
                sendResolved(head.line, head.recipients, head.translation.join());
            } else if (now - head.deadlineAt >= 0) {
                lane.pollFirst();
                metrics.incrementGlobal("delivery_deadline_missed_total");
                head.recipients.forEach(recipient -> sendOriginal(head.line, recipient));
                followUpLate(head);
            } else {
                break;
            }
        }

        if (lane.isEmpty()) {
            lanes.remove(language);
        }
    }

    private void followUpLate(PendingLine pending) {
        if (TranslationDeliveryConfig.MODE_WAIT.equals(config.translation.delivery.mode)) {
            return;
        }

        long lateNanos = TimeUnit.MILLISECONDS.toNanos(config.translation.delivery.lateTranslationMillis);
        pending.translation.thenAccept(result -> async.main(() -> {
            if (!(result instanceof ChatTranslation.Translated(var translated))) {
                return;
            }

            if (nanoTime.getAsLong() - pending.createdAt > lateNanos) {
                metrics.incrementGlobal("late_translation_dropped_total");
                return;
            }

            metrics.incrementGlobal("late_translation_sent_total");
            String followUp = "[lightgray]» " + escape(pending.line.authorName()) + ": " + escape(translated);
            for (Recipient recipient : pending.recipients) {
                if (Async.isPlayerOnline(recipient.session.player)) {
                    recipient.session.player.sendMessage(followUp);
                }
            }
        }));
    }

    private void sendResolved(ChatLine line, List<Recipient> recipients, ChatTranslation result) {
        if (result instanceof ChatTranslation.Translated(var translated)) {
            String suffix = " [white]([lightgray]" + escape(translated) + "[])";
            String raw = line.text() + " (" + translated + ")";
            recipients.forEach(recipient -> send(line, recipient, recipient.formatted + suffix, raw));
            return;
        }

        recipients.forEach(recipient -> sendOriginal(line, recipient));
    }

    private void sendOriginal(ChatLine line, Recipient recipient) {
        send(line, recipient, recipient.formatted, line.text());
    }

    /**
     * The {@code Player} references were captured when the message was sent; a translation can
     * take long enough that a recipient has left by now.
     */
    private void send(ChatLine line, Recipient recipient, String formatted, String raw) {
        Player player = recipient.session.player;
        if (!Async.isPlayerOnline(player)) {
            return;
        }

        if (line.author() == null) {
            player.sendMessage(formatted);
        } else if (recipient.passRaw) {
            player.sendMessage(formatted, line.author(), raw);
        } else {
            player.sendMessage(formatted, line.author());
        }
    }

    private long deadlineMillis() {
        var delivery = config.translation.delivery;
        return switch (delivery.mode) {
            case TranslationDeliveryConfig.MODE_ORIGINAL_FIRST -> 0L;
            case TranslationDeliveryConfig.MODE_WAIT -> delivery.lateTranslationMillis;
            default -> delivery.deadlineMillis;
        };
    }

    /** Provider output is shown inside a colour span; a stray {@code [} must not open a tag. */
    private static String escape(String text) {
        return text.replace("[", "[[");
    }

    /** Number of lines waiting for a translation. Visible for tests. */
    int pendingCount() {
        return lanes.values().stream().mapToInt(ArrayDeque::size).sum();
    }

    private record ChatLine(Player author, String authorName, String authorKey, String text) {
    }

    private record Recipient(Session session, String formatted, boolean passRaw, boolean self) {
        private Recipient(Session session, String formatted, boolean passRaw) {
            this(session, formatted, passRaw, false);
        }
    }

    private record PendingLine(ChatLine line,
                               List<Recipient> recipients,
                               CompletableFuture<ChatTranslation> translation,
                               long createdAt,
                               long deadlineAt) {
    }
}
