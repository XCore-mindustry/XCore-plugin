package org.xcore.plugin.event.transport;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.xcore.plugin.common.PLog;
import org.xcore.plugin.config.TomlXcoreConfig;
import org.xcore.plugin.model.AuditActor;
import org.xcore.plugin.rating.ladder.LadderService;
import org.xcore.plugin.rating.prize.PrizeService;
import org.xcore.plugin.rating.season.Season;
import org.xcore.plugin.rating.season.SeasonException;
import org.xcore.plugin.rating.season.SeasonLifecycleService;
import org.xcore.plugin.rating.season.SeasonReschedule;
import org.xcore.plugin.service.NetworkService;
import org.xcore.plugin.service.network.RatingProtocolMapper;
import org.xcore.protocol.generated.messages.rating.RatingMessages.RatingAccountsMergeRequestV1;
import org.xcore.protocol.generated.messages.rating.RatingMessages.RatingPrizeGrantUpdateRequestV1;
import org.xcore.protocol.generated.messages.rating.RatingMessages.RatingSeasonPrizesSetRequestV1;
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
    private final PrizeService prizes;

    @Inject
    public RatingTransportHandler(NetworkService network,
                                  TomlXcoreConfig config,
                                  SeasonLifecycleService lifecycle,
                                  LadderService ladders,
                                  PrizeService prizes) {
        this.network = network;
        this.config = config;
        this.lifecycle = lifecycle;
        this.ladders = ladders;
        this.prizes = prizes;
    }

    public void registerListeners() {
        network.subscribe(RatingSeasonRescheduleRequestV1.class, this::reschedule);
        network.subscribe(RatingAccountsMergeRequestV1.class, this::merge);
        network.subscribe(RatingSeasonPrizesSetRequestV1.class, this::setPrizes);
        network.subscribe(RatingPrizeGrantUpdateRequestV1.class, this::updateGrants);
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

    void setPrizes(RatingSeasonPrizesSetRequestV1 request) {
        if (!request.server().equals(config.server.name)) return;
        try {
            AuditActor actor = RatingProtocolMapper.toAuditActor(request.actor());
            Season season = switch (request.operation()) {
                case ADD -> {
                    if (request.prize() == null) {
                        throw new SeasonException("add needs a prize");
                    }
                    yield lifecycle.addPrize(request.ladder(), RatingProtocolMapper.toPrize(request.prize()), actor);
                }
                case REMOVE -> {
                    if (request.placeFrom() == null) {
                        throw new SeasonException("remove needs placeFrom");
                    }
                    int from = request.placeFrom();
                    yield lifecycle.removePrizes(request.ladder(), from,
                            request.placeTo() == null ? from : request.placeTo(), actor);
                }
            };
            network.respond(request, RatingProtocolMapper.toPrizesResponse(request.server(), season));
        } catch (SeasonException | IllegalArgumentException e) {
            network.respondError(request, REJECTED, String.valueOf(e.getMessage()));
        } catch (RuntimeException e) {
            PLog.err("Prize request for '@' failed: @", request.ladder(), e.getMessage());
            network.respondError(request, FAILED, "The prizes could not be changed; see the server log");
        }
    }

    void updateGrants(RatingPrizeGrantUpdateRequestV1 request) {
        if (!request.server().equals(config.server.name)) return;
        try {
            int updated = prizes.markDelivered(request.ladder(), request.season(), request.place(), request.playerPid(),
                    RatingProtocolMapper.toAuditActor(request.actor()), request.note());
            network.respond(request, RatingProtocolMapper.toGrantUpdateResponse(
                    request.server(), request.season(), request.place(), updated));
        } catch (SeasonException e) {
            network.respondError(request, REJECTED, String.valueOf(e.getMessage()));
        } catch (RuntimeException e) {
            PLog.err("Prize delivery update for '@' failed: @", request.ladder(), e.getMessage());
            network.respondError(request, FAILED, "The prize could not be updated; see the server log");
        }
    }

    /** Where the season's end was before the change that produced this state. */
    private static Instant previousEnd(Season season) {
        return season.rescheduled().isEmpty()
                ? season.endsAt()
                : season.rescheduled().get(season.rescheduled().size() - 1).from();
    }
}
