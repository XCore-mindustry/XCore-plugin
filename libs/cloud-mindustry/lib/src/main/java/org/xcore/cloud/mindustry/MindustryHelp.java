package org.xcore.cloud.mindustry;

import arc.util.CommandHandler;
import org.incendo.cloud.Command;
import org.incendo.cloud.caption.Caption;
import org.incendo.cloud.caption.CaptionVariable;
import org.incendo.cloud.component.CommandComponent;
import org.incendo.cloud.description.Description;
import org.incendo.cloud.help.HelpQuery;
import org.incendo.cloud.help.result.CommandEntry;
import org.incendo.cloud.help.result.HelpQueryResult;
import org.incendo.cloud.help.result.IndexCommandResult;
import org.incendo.cloud.help.result.MultipleCommandResult;
import org.incendo.cloud.help.result.VerboseCommandResult;
import org.xcore.cloud.mindustry.parser.MindustryCaptionKeys;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiPredicate;

/**
 * Renders Cloud's help results as chat or console messages.
 * <p>
 * Nothing is registered by creating one: add your own help command and call {@link #sendIndex}
 * and {@link #sendQuery} from it. A sender only sees commands they have permission for, that
 * are actually published in the Arc handler (under the name they were published with, which
 * differs from the Cloud name after a {@link ConflictStrategy#PREFIX} collision), and, for
 * player-only commands, only when they are a player. Every line is a caption from
 * {@link MindustryCaptionKeys}.
 */
public final class MindustryHelp<C> {

    private record Line(String syntax, String description) {}

    private final MindustryCommandManager<C> manager;
    private final int pageSize;
    private final BiPredicate<C, Command<C>> visibility;
    private BiPredicate<C, CommandHandler.Command> legacyVisibility;

    public MindustryHelp(MindustryCommandManager<C> manager, int pageSize) {
        this(manager, pageSize, (sender, command) -> true);
    }

    /**
     * @param visibility an extra filter on top of permissions and the player-only check
     */
    public MindustryHelp(MindustryCommandManager<C> manager, int pageSize, BiPredicate<C, Command<C>> visibility) {
        if (pageSize <= 0) {
            throw new IllegalArgumentException("pageSize must be positive: " + pageSize);
        }
        this.manager = Objects.requireNonNull(manager);
        this.pageSize = pageSize;
        this.visibility = Objects.requireNonNull(visibility);
    }

    /**
     * Also lists the commands of the Arc handler that were not registered through Cloud. They
     * have no Cloud permission, so {@code visibility} alone decides who sees them.
     */
    public MindustryHelp<C> includeLegacyCommands(BiPredicate<C, CommandHandler.Command> visibility) {
        this.legacyVisibility = Objects.requireNonNull(visibility);
        return this;
    }

    /**
     * Sends one page of every command {@code sender} can use. Pages start at 1.
     */
    public void sendIndex(C sender, int page) {
        Map<String, String> published = manager.publishedNames();
        sendList(sender, MindustryCaptionKeys.HELP_TITLE, "", index(sender, published), page,
                MindustryCaptionKeys.HELP_EMPTY);
    }

    /**
     * Sends help for {@code query}: the usage of the command it names, the subcommands below it,
     * or one page of the commands starting with it.
     */
    public void sendQuery(C sender, String query, int page) {
        String trimmed = query.trim();
        if (trimmed.isEmpty()) {
            sendIndex(sender, page);
            return;
        }

        Map<String, String> published = manager.publishedNames();
        HelpQueryResult<C> result = manager.createHelpHandler(command -> isVisible(sender, command, published))
                .query(HelpQuery.of(sender, toCloudName(trimmed, published)));

        if (result instanceof VerboseCommandResult<C> verbose) {
            sendUsage(sender, verbose.entry(), published);
        } else if (result instanceof MultipleCommandResult<C> multiple) {
            List<Line> lines = new ArrayList<>();
            for (String suggestion : multiple.childSuggestions()) {
                lines.add(new Line(physical(suggestion, published), ""));
            }
            sendList(sender, MindustryCaptionKeys.HELP_TITLE_QUERY, physical(multiple.longestPath(), published),
                    lines, page, MindustryCaptionKeys.HELP_NO_MATCH, CaptionVariable.of("query", trimmed));
        } else {
            List<Line> lines = new ArrayList<>();
            if (result instanceof IndexCommandResult<C> index) {
                for (CommandEntry<C> entry : index.entries()) {
                    lines.add(line(entry, published));
                }
            }
            String root = stripPrefix(trimmed).split(" ", 2)[0];
            for (CommandHandler.Command legacy : legacyCommands(sender)) {
                if (legacy.text.regionMatches(true, 0, root, 0, root.length())) {
                    lines.add(line(legacy));
                }
            }
            lines.sort(Comparator.comparing(Line::syntax));
            sendList(sender, MindustryCaptionKeys.HELP_TITLE_QUERY, trimmed, lines, page,
                    MindustryCaptionKeys.HELP_NO_MATCH, CaptionVariable.of("query", trimmed));
        }
    }

    private List<Line> index(C sender, Map<String, String> published) {
        List<Line> lines = new ArrayList<>();
        for (CommandEntry<C> entry : manager.createHelpHandler(command -> isVisible(sender, command, published))
                .queryRootIndex(sender).entries()) {
            lines.add(line(entry, published));
        }
        for (CommandHandler.Command legacy : legacyCommands(sender)) {
            lines.add(line(legacy));
        }
        lines.sort(Comparator.comparing(Line::syntax));
        return lines;
    }

    private boolean isVisible(C sender, Command<C> command, Map<String, String> published) {
        if (!published.containsKey(command.rootComponent().name())) return false;
        // Cloud only checks permissions on commands, so a branch leading to nothing but
        // restricted commands would still be listed.
        if (!manager.testPermission(sender, command.commandPermission()).allowed()) return false;
        if (manager.isPlayerOnly(command) && !manager.senderMapper().reverse(sender).isPlayer()) return false;
        return visibility.test(sender, command);
    }

    private List<CommandHandler.Command> legacyCommands(C sender) {
        List<CommandHandler.Command> result = new ArrayList<>();
        if (legacyVisibility == null) return result;
        for (CommandHandler.Command command : manager.commandHandler().getCommandList()) {
            if (!(command instanceof MindustryCloudCommand) && legacyVisibility.test(sender, command)) {
                result.add(command);
            }
        }
        return result;
    }

    private Line line(CommandEntry<C> entry, Map<String, String> published) {
        return new Line(physical(entry.syntax(), published), description(entry.command()));
    }

    private Line line(CommandHandler.Command legacy) {
        String syntax = legacy.paramText.isEmpty() ? legacy.text : legacy.text + " " + legacy.paramText;
        return new Line(manager.commandHandler().getPrefix() + syntax, legacy.description);
    }

    private static <C> String description(Command<C> command) {
        Description description = command.commandDescription().description();
        if (description.isEmpty()) {
            description = command.rootComponent().description();
        }
        return description.textDescription();
    }

    /** Cloud syntax starts with the Cloud root name; players have to type the published one. */
    private String physical(String syntax, Map<String, String> published) {
        int end = syntax.indexOf(' ');
        String root = end < 0 ? syntax : syntax.substring(0, end);
        String name = published.getOrDefault(root, root);
        return manager.commandHandler().getPrefix() + name + (end < 0 ? "" : syntax.substring(end));
    }

    /** The reverse of {@link #physical}: lets a query name a command the way it is typed. */
    private String toCloudName(String query, Map<String, String> published) {
        query = stripPrefix(query);
        int end = query.indexOf(' ');
        String root = end < 0 ? query : query.substring(0, end);
        for (Map.Entry<String, String> entry : published.entrySet()) {
            if (entry.getValue().equalsIgnoreCase(root)) {
                return entry.getKey() + (end < 0 ? "" : query.substring(end));
            }
        }
        return query;
    }

    private String stripPrefix(String query) {
        String prefix = manager.commandHandler().getPrefix();
        return !prefix.isEmpty() && query.startsWith(prefix) ? query.substring(prefix.length()) : query;
    }

    private void sendUsage(C sender, CommandEntry<C> entry, Map<String, String> published) {
        List<String> out = new ArrayList<>();
        out.add(format(sender, MindustryCaptionKeys.HELP_USAGE,
                CaptionVariable.of("syntax", physical(entry.syntax(), published))));

        String description = description(entry.command());
        if (!description.isEmpty()) {
            out.add(format(sender, MindustryCaptionKeys.HELP_DESCRIPTION, CaptionVariable.of("description", description)));
        }

        for (CommandComponent<C> component : entry.command().components()) {
            if (component.type() == CommandComponent.ComponentType.LITERAL || component.description().isEmpty()) {
                continue;
            }
            out.add(format(sender, MindustryCaptionKeys.HELP_ARGUMENT,
                    CaptionVariable.of("name", component.name()),
                    CaptionVariable.of("description", component.description().textDescription())));
        }
        send(sender, out);
    }

    private void sendList(C sender, Caption title, String query, List<Line> lines, int page,
                          Caption emptyCaption, CaptionVariable... emptyVariables) {
        if (lines.isEmpty()) {
            manager.sendErrorMessage(sender, format(sender, emptyCaption, emptyVariables));
            return;
        }

        int pages = (lines.size() + pageSize - 1) / pageSize;
        if (page < 1 || page > pages) {
            manager.sendErrorMessage(sender, format(sender, MindustryCaptionKeys.HELP_INVALID_PAGE,
                    CaptionVariable.of("page", String.valueOf(page)),
                    CaptionVariable.of("pages", String.valueOf(pages))));
            return;
        }

        List<String> out = new ArrayList<>();
        out.add(format(sender, title,
                CaptionVariable.of("query", query),
                CaptionVariable.of("page", String.valueOf(page)),
                CaptionVariable.of("pages", String.valueOf(pages))));

        for (Line line : lines.subList((page - 1) * pageSize, Math.min(page * pageSize, lines.size()))) {
            out.add(line.description().isEmpty()
                    ? format(sender, MindustryCaptionKeys.HELP_ENTRY, CaptionVariable.of("syntax", line.syntax()))
                    : format(sender, MindustryCaptionKeys.HELP_ENTRY_DESCRIBED,
                            CaptionVariable.of("syntax", line.syntax()),
                            CaptionVariable.of("description", line.description())));
        }

        if (page < pages) {
            out.add(format(sender, MindustryCaptionKeys.HELP_FOOTER,
                    CaptionVariable.of("page", String.valueOf(page)),
                    CaptionVariable.of("pages", String.valueOf(pages)),
                    CaptionVariable.of("next", String.valueOf(page + 1))));
        }
        send(sender, out);
    }

    private String format(C sender, Caption caption, CaptionVariable... variables) {
        return manager.captionFormatter().formatCaption(
                caption, sender, manager.captionRegistry().caption(caption, sender), variables);
    }

    private void send(C sender, List<String> lines) {
        manager.senderMapper().reverse(sender).sendMessage(String.join("\n", lines));
    }
}
