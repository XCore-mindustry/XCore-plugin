package org.xcore.plugin.rating.prize;

import arc.util.Log;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.xcore.plugin.common.PLog;
import org.xcore.plugin.model.AuditActor;
import org.xcore.plugin.rating.season.PrizeKind;
import org.xcore.plugin.rating.season.Season;
import org.xcore.plugin.rating.season.SeasonException;
import org.xcore.plugin.rating.season.SeasonPodiumEntry;
import org.xcore.plugin.rating.season.SeasonPrize;
import org.xcore.plugin.rating.season.SeasonPrizes;
import org.xcore.plugin.rating.season.SeasonStore;

import java.time.Clock;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Turns the prizes of a finished season into grants and gives the ones a handler can give.
 *
 * <p>A grant is created once per (season, place, player, prize) and a handler only runs for a
 * grant that is still pending, so the whole step can be repeated after a crash. Everything here
 * is blocking.</p>
 */
@Singleton
public class PrizeService implements SeasonPrizes {
    private final PrizeGrantRepository grants;
    private final SeasonStore seasons;
    private final Map<PrizeKind, PrizeHandler> handlers = new EnumMap<>(PrizeKind.class);
    private final Clock clock;

    @Inject
    public PrizeService(PrizeGrantRepository grants, SeasonStore seasons, List<PrizeHandler> handlers) {
        this(grants, seasons, handlers, Clock.systemUTC());
    }

    public PrizeService(PrizeGrantRepository grants, SeasonStore seasons, List<PrizeHandler> handlers, Clock clock) {
        this.grants = Objects.requireNonNull(grants, "grants");
        this.seasons = Objects.requireNonNull(seasons, "seasons");
        this.clock = Objects.requireNonNull(clock, "clock");
        for (PrizeHandler handler : handlers) {
            if (this.handlers.put(handler.kind(), handler) != null) {
                throw new IllegalArgumentException("Two handlers for prize kind " + handler.kind());
            }
        }
    }

    @Override
    public void validate(SeasonPrize prize) {
        handler(prize.kind()).validate(prize);
    }

    @Override
    public void award(Season archived) {
        List<SeasonPrize> prizes = archived.prizes();
        if (prizes.isEmpty()) return;

        for (SeasonPodiumEntry entry : archived.podium()) {
            for (int index = 0; index < prizes.size(); index++) {
                if (prizes.get(index).covers(entry.place())) {
                    grants.createIfAbsent(PrizeGrant.pending(archived.id(), entry, index, prizes.get(index),
                            clock.instant()));
                }
            }
        }

        RuntimeException failure = null;
        for (PrizeGrant grant : grants.findBySeason(archived.id())) {
            if (grant.status() != PrizeStatus.PENDING || !grant.kind().automatic()) continue;
            try {
                deliver(grant);
            } catch (RuntimeException e) {
                // A transient failure leaves the grant pending; the finalisation is retried.
                Log.err("Prize " + grant.id() + " could not be delivered", e);
                if (failure == null) failure = e;
            }
        }
        if (failure != null) throw failure;
    }

    private void deliver(PrizeGrant grant) {
        PrizeOutcome outcome = handler(grant.kind()).deliver(grant);
        if (outcome.status() == PrizeStatus.PENDING) return;
        if (grants.transition(grant.id(), PrizeStatus.PENDING, outcome.status(), PrizeGrant.SYSTEM, outcome.note(),
                clock.instant()) && outcome.status() == PrizeStatus.FAILED) {
            PLog.warn("Prize @ failed: @", grant.id(), outcome.note());
        }
    }

    /** Blocking. The grants of one season, ordered by place. */
    public List<PrizeGrant> grants(String ladderId, int seasonNumber) {
        return grants.findBySeason(Season.id(ladderId, seasonNumber));
    }

    /**
     * Blocking. Records that a person handed over the prizes of one place.
     *
     * @return how many grants were marked
     * @throws SeasonException when the season does not exist or nothing at that place is waiting
     */
    public int markDelivered(String ladderId, int seasonNumber, int place, AuditActor actor, String note) {
        Season season = seasons.find(ladderId, seasonNumber)
                .orElseThrow(() -> new SeasonException("Season " + Season.id(ladderId, seasonNumber) + " does not exist"));
        String by = actor.type == null ? "system" : actor.type.name().toLowerCase();
        by += ":" + (actor.id == null || actor.id.isBlank() ? "unknown" : actor.id);
        int changed = grants.markDelivered(season.id(), place, by, note == null ? "" : note.strip(), clock.instant());
        if (changed == 0) {
            throw new SeasonException("No prize of season " + season.id() + " is waiting at place " + place);
        }
        PLog.info("Prizes of season @ place @ marked delivered by @ (@)", season.id(), place, by, changed);
        return changed;
    }

    private PrizeHandler handler(PrizeKind kind) {
        PrizeHandler handler = handlers.get(kind);
        if (handler == null) {
            throw new SeasonException("Prizes of kind " + kind + " cannot be delivered here");
        }
        return handler;
    }
}
