package org.xcore.plugin.command.controller.server;

import arc.util.Log;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.incendo.cloud.annotation.specifier.Greedy;
import org.incendo.cloud.annotations.Argument;
import org.incendo.cloud.annotations.Command;
import org.incendo.cloud.annotations.CommandDescription;
import org.jspecify.annotations.Nullable;
import org.xcore.plugin.cloud.XCoreSender;
import org.xcore.plugin.command.controller.CloudServerController;
import org.xcore.plugin.common.PLog;
import org.xcore.plugin.concurrent.Async;
import org.xcore.plugin.model.AuditActor;
import org.xcore.plugin.model.AuditActorType;
import org.xcore.plugin.rating.ladder.LadderStore;
import org.xcore.plugin.rating.prize.PrizeGrant;
import org.xcore.plugin.rating.prize.PrizeService;
import org.xcore.plugin.rating.season.PrizeKind;
import org.xcore.plugin.rating.season.Season;
import org.xcore.plugin.rating.season.SeasonCommandParser;
import org.xcore.plugin.rating.season.SeasonException;
import org.xcore.plugin.rating.season.SeasonLifecycleService;
import org.xcore.plugin.rating.season.SeasonPodiumEntry;
import org.xcore.plugin.rating.season.SeasonPrize;
import org.xcore.plugin.rating.season.SeasonReschedule;
import org.xcore.plugin.rating.season.SeasonSchedule;
import org.xcore.plugin.rating.season.SeasonStatus;
import org.xcore.plugin.rating.season.SeasonStore;

import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.function.Supplier;

/** Console management of rating seasons. Storage work runs off the server thread. */
@Singleton
public class SeasonController implements CloudServerController {
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm z");

    private final SeasonStore seasons;
    private final SeasonLifecycleService lifecycle;
    private final SeasonSchedule schedule;
    private final LadderStore standings;
    private final PrizeService prizes;
    private final Async async;

    @Inject
    public SeasonController(SeasonStore seasons,
                            SeasonLifecycleService lifecycle,
                            SeasonSchedule schedule,
                            LadderStore standings,
                            PrizeService prizes,
                            Async async) {
        this.seasons = seasons;
        this.lifecycle = lifecycle;
        this.schedule = schedule;
        this.standings = standings;
        this.prizes = prizes;
        this.async = async;
    }

    @Command("season list [ladder]")
    @CommandDescription("Lists rating seasons of one ladder, or of every ladder.")
    public void list(XCoreSender sender,
                     @Nullable @Argument(value = "ladder", description = "Ladder ID, e.g. minipvp or hexed") String ladder) {
        run(() -> {
            List<Season> found = ladder == null ? seasons.all() : seasons.list(ladder);
            if (found.isEmpty()) {
                PLog.info("No seasons found.");
                return;
            }
            PLog.info("Rating seasons (@):", found.size());
            for (Season season : found) {
                PLog.info("  @ &fb@&fr  @ -> @  matches: @", season.id(), season.status(),
                        time(season.startsAt()), time(season.endsAt()), season.matches());
            }
        });
    }

    @Command("season info <ladder>")
    @CommandDescription("Shows the running season of a ladder and the season before it.")
    public void info(XCoreSender sender,
                     @Argument(value = "ladder", description = "Ladder ID, e.g. minipvp or hexed") String ladder) {
        run(() -> {
            List<Season> found = seasons.list(ladder);
            if (found.isEmpty()) {
                PLog.info("Ladder '@' has no seasons.", ladder);
                return;
            }
            found.stream().limit(2).forEach(this::describe);
        });
    }

    @Command("season extend <ladder> <duration> [reason]")
    @CommandDescription("Moves the end of the running season later by a time span such as 3d, 2w or 1mo.")
    public void extend(XCoreSender sender,
                       @Argument(value = "ladder", description = "Ladder ID, e.g. minipvp or hexed") String ladder,
                       @Argument(value = "duration", description = "Time span: 12h, 3d, 2w, 1mo") String duration,
                       @Nullable @Argument(value = "reason", description = "Audit reason") @Greedy String reason) {
        change(() -> lifecycle.extend(ladder,
                end -> SeasonCommandParser.extend(end, duration, schedule.zone()), console(), reason));
    }

    @Command("season end-at <ladder> <datetime> [reason]")
    @CommandDescription("Sets the end of the running season: 2027-01-31 or 2027-01-31T18:00, in the seasons time zone.")
    public void endAt(XCoreSender sender,
                      @Argument(value = "ladder", description = "Ladder ID, e.g. minipvp or hexed") String ladder,
                      @Argument(value = "datetime", description = "yyyy-MM-dd or yyyy-MM-ddTHH:mm") String datetime,
                      @Nullable @Argument(value = "reason", description = "Audit reason") @Greedy String reason) {
        change(() -> lifecycle.reschedule(ladder, SeasonCommandParser.dateTime(datetime, schedule.zone()),
                console(), reason));
    }

    @Command("season end-now <ladder> confirm [reason]")
    @CommandDescription("Ends the running season immediately and starts the next one. This cannot be undone.")
    public void endNow(XCoreSender sender,
                       @Argument(value = "ladder", description = "Ladder ID, e.g. minipvp or hexed") String ladder,
                       @Nullable @Argument(value = "reason", description = "Audit reason") @Greedy String reason) {
        change(() -> lifecycle.endNow(ladder, console(), reason));
    }

    @Command("season prize list <ladder> [season]")
    @CommandDescription("Shows the prizes of a season and who they were granted to.")
    public void prizeList(XCoreSender sender,
                          @Argument(value = "ladder", description = "Ladder ID, e.g. minipvp or hexed") String ladder,
                          @Nullable @Argument(value = "season", description = "Season number; the running one by default") Integer season) {
        run(() -> {
            Season found = season == null
                    ? seasons.list(ladder).stream().findFirst().orElseThrow(() -> new SeasonException("Ladder '" + ladder + "' has no seasons"))
                    : seasons.find(ladder, season).orElseThrow(() -> new SeasonException("Season " + ladder + ":" + season + " does not exist"));
            if (found.prizes().isEmpty()) {
                PLog.info("Season @ has no prizes.", found.id());
            }
            for (SeasonPrize prize : found.prizes()) {
                PLog.info("  place @: @ &fb@&fr@", prize.places(), prize.kind().name().toLowerCase(), prize.value(),
                        prize.description().isBlank() ? "" : " - " + prize.description());
            }
            for (PrizeGrant grant : prizes.grants(ladder, found.number())) {
                PLog.info("  grant #@ @: @ &fb@&fr by @@", grant.place(), grant.playerUuid(), grant.value(),
                        grant.status(), grant.grantedBy(), grant.note().isBlank() ? "" : " (" + grant.note() + ")");
            }
        });
    }

    @Command("season prize set <ladder> <places> <kind> <value>")
    @CommandDescription("Adds a prize to the running season: badge <badge-id>, or custom <free text>.")
    public void prizeSet(XCoreSender sender,
                         @Argument(value = "ladder", description = "Ladder ID, e.g. minipvp or hexed") String ladder,
                         @Argument(value = "places", description = "A place or a range: 1 or 1-3") String places,
                         @Argument(value = "kind", description = "badge or custom") String kind,
                         @Argument(value = "value", description = "Badge ID, or the prize text") @Greedy String value) {
        run(() -> {
            int[] range = SeasonCommandParser.places(places);
            Season season = lifecycle.addPrize(ladder,
                    new SeasonPrize(range[0], range[1], PrizeKind.parse(kind), value, ""), console());
            PLog.info("&gSeason @ now has @ prize(s)", season.id(), season.prizes().size());
        });
    }

    @Command("season prize clear <ladder> <places>")
    @CommandDescription("Removes the prizes of the running season that lie within the given places.")
    public void prizeClear(XCoreSender sender,
                           @Argument(value = "ladder", description = "Ladder ID, e.g. minipvp or hexed") String ladder,
                           @Argument(value = "places", description = "A place or a range: 1 or 1-3") String places) {
        run(() -> {
            int[] range = SeasonCommandParser.places(places);
            Season season = lifecycle.removePrizes(ladder, range[0], range[1], console());
            PLog.info("&gSeason @ now has @ prize(s)", season.id(), season.prizes().size());
        });
    }

    @Command("season prize delivered <ladder> <season> <place> [note]")
    @CommandDescription("Records that the prizes of a finished season's place were handed over.")
    public void prizeDelivered(XCoreSender sender,
                               @Argument(value = "ladder", description = "Ladder ID, e.g. minipvp or hexed") String ladder,
                               @Argument(value = "season", description = "Season number") int season,
                               @Argument(value = "place", description = "Place on the podium") int place,
                               @Nullable @Argument(value = "note", description = "For example, the gift code that was sent") @Greedy String note) {
        run(() -> PLog.info("&gMarked @ prize(s) of place @ delivered",
                prizes.markDelivered(ladder, season, place, console(), note), place));
    }

    private void describe(Season season) {
        Instant now = Instant.now();
        PLog.info("Season @ &fb@&fr", season.id(), season.status());
        PLog.info("  Runs:         @ -> @", time(season.startsAt()), time(season.endsAt()));
        if (season.active()) {
            PLog.info("  Remaining:    @", span(season.remaining(now)));
            PLog.info("  Participants: @", standings.count(season.ladderId(), season.number()));
            PLog.info("  Matches:      @", season.matches());
            PLog.info("  Notices sent: @", season.sentNotices().isEmpty() ? "none" : String.join(", ", season.sentNotices()));
        } else if (season.status() == SeasonStatus.CLOSING) {
            PLog.info("  Archiving at: @", time(season.endsAt().plus(schedule.settlementGrace())));
        } else if (season.summary() != null) {
            PLog.info("  Participants: @", season.summary().participants());
            PLog.info("  Matches:      @", season.summary().matches());
        }
        for (SeasonReschedule change : season.rescheduled()) {
            PLog.info("  Moved:        @ -> @ by @ at @@", time(change.from()), time(change.to()), change.actor(),
                    time(change.at()), change.reason().isBlank() ? "" : " (" + change.reason() + ")");
        }
        for (SeasonPrize prize : season.prizes()) {
            PLog.info("  Prize @: @ @", prize.places(), prize.kind().name().toLowerCase(), prize.label());
        }
        for (SeasonPodiumEntry entry : season.podium()) {
            PLog.info("  #@ @ (#@) @ @, @ matches@", entry.place(), entry.nickname(), entry.pid(), entry.rating(),
                    entry.league(), entry.matches(),
                    entry.discordLinked() ? ", Discord " + entry.discordUsername() + " (" + entry.discordId() + ")" : "");
        }
    }

    private void change(Supplier<Season> operation) {
        run(() -> {
            Season season = operation.get();
            PLog.info("&gSeason @ is now @ and ends @", season.id(), season.status(), time(season.endsAt()));
        });
    }

    private void run(Runnable task) {
        async.run(() -> {
            try {
                task.run();
            } catch (SeasonException | IllegalArgumentException e) {
                PLog.err("@", e.getMessage());
            } catch (RuntimeException e) {
                PLog.err("Season command failed: @", e.getMessage());
                Log.err(e);
            }
        });
    }

    private String time(Instant instant) {
        return TIME.format(instant.atZone(schedule.zone()));
    }

    private static String span(Duration duration) {
        return duration.toDays() + "d " + duration.toHoursPart() + "h " + duration.toMinutesPart() + "m";
    }

    private static AuditActor console() {
        return AuditActor.builder()
                .type(AuditActorType.SERVER_CONSOLE)
                .nameSnapshot("Console")
                .id("console")
                .build();
    }
}
