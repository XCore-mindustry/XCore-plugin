package org.xcore.plugin.event.transport;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.xcore.plugin.common.PLog;
import org.xcore.plugin.config.TomlXcoreConfig;
import org.xcore.plugin.model.AuditActor;
import org.xcore.plugin.rating.ladder.LadderService;
import org.xcore.plugin.rating.season.Season;
import org.xcore.plugin.rating.season.SeasonException;
import org.xcore.plugin.rating.season.SeasonLifecycleService;
import org.xcore.plugin.rating.season.SeasonReschedule;
import org.xcore.plugin.service.NetworkService;
import org.xcore.plugin.service.network.RatingProtocolMapper;
import org.xcore.protocol.generated.messages.rating.RatingMessages.RatingAccountsMergeRequestV1;
import org.xcore.protocol.generated.messages.rating.RatingMessages.RatingSeasonRescheduleRequestV1;

import java.time.Instant;
import java.time.DateTimeException;

/**
 * Answers the rating requests of other services: moving or ending a season, and folding one
 * account's standings into another when accounts are merged.
 *
 * <p>The seasons and standings live in the shared database, so any server can answer for any
 * ladder; the caller names the server it picked. Requests arrive on a redis-sub thread, where
 * blocking storage work is fine.</p>
 */
@Singleton
public class RatingTransportHandler {
    static final String REJECTED = "REJECTED";
    static final String FAILED = "FAILED";

    private final NetworkService network;
    private final TomlXcoreConfig config;
    private final SeasonLifecycleService lifecycle;
    private final LadderService ladders;

    @Inject
    public RatingTransportHandler(NetworkService network,
                                  TomlXcoreConfig config,
                                  SeasonLifecycleService lifecycle,
                                  LadderService ladders) {
        this.network = network;
        this.config = config;
        this.lifecycle = lifecycle;
        this.ladders = ladders;
    }

    public void registerListeners() {
        network.subscribe(RatingSeasonRescheduleRequestV1.class, this::reschedule);
        network.subscribe(RatingAccountsMergeRequestV1.class, this::merge);
    }

    void reschedule(RatingSeasonRescheduleRequestV1 request) {
        if (!request.server().equals(config.server.name)) return;
        try {
            AuditActor actor = RatingProtocolMapper.toAuditActor(request.actor());
            String ladder = request.ladder();
            boolean ended = false;
            Season season;
            switch (request.operation()) {
                case EXTEND -> {
                    Integer seconds = request.extendSeconds();
                    if (seconds == null) {
                        throw new SeasonException("extend needs extendSeconds");
                    }
                    season = lifecycle.extend(ladder, end -> end.plusSeconds(seconds), actor, request.reason());
                }
                case SET_END -> {
                    if (request.endsAt() == null) {
                        throw new SeasonException("set-end needs endsAt");
                    }
                    season = lifecycle.reschedule(ladder, Instant.parse(request.endsAt()), actor, request.reason());
                }
                case END_NOW -> {
                    season = lifecycle.endNow(ladder, actor, request.reason());
                    ended = true;
                }
                default -> throw new SeasonException("Unsupported operation " + request.operation());
            }
            network.respond(request, RatingProtocolMapper.toRescheduleResponse(
                    request.server(), season, previousEnd(season), ended));
        } catch (SeasonException | DateTimeException | ArithmeticException e) {
            network.respondError(request, REJECTED, String.valueOf(e.getMessage()));
        } catch (RuntimeException e) {
            PLog.err("Season request for '@' failed: @", request.ladder(), e.getMessage());
            network.respondError(request, FAILED, "The season could not be changed; see the server log");
        }
    }

    void merge(RatingAccountsMergeRequestV1 request) {
        if (!request.server().equals(config.server.name)) return;
        try {
            int merged = ladders.mergePlayer(null, request.sourceUuid(), request.targetUuid());
            network.respond(request, RatingProtocolMapper.toMergeResponse(request.server(), merged));
        } catch (RuntimeException e) {
            PLog.err("Rating merge of @ into @ failed: @", request.sourceUuid(), request.targetUuid(),
                    e.getMessage());
            network.respondError(request, FAILED, "The standings could not be merged; see the server log");
        }
    }

    /** Where the season's end was before the change that produced this state. */
    private static Instant previousEnd(Season season) {
        return season.rescheduled().isEmpty()
                ? season.endsAt()
                : season.rescheduled().get(season.rescheduled().size() - 1).from();
    }
}
