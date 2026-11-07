package org.xcore.plugin.service.stats;

import io.avaje.inject.PostConstruct;
import io.avaje.inject.PreDestroy;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.xcore.plugin.config.TomlXcoreConfig;
import org.xcore.plugin.database.repository.PlayerDataRepository;
import org.xcore.plugin.integration.profile.ProfileSection;
import org.xcore.plugin.integration.profile.ProfileSectionProvider;
import org.xcore.plugin.integration.profile.ProfileSectionRegistry;
import org.xcore.plugin.integration.profile.ProfileSectionView;
import org.xcore.plugin.model.PlayerData;
import org.xcore.plugin.model.enums.TopCategory;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

import static com.ospx.flubundle.Bundle.args;

/** The retired Hexed rank, shown apart from the current statistics to players who earned points or on Hexed servers. */
@Singleton
public class LegacyHexedProfileSection implements ProfileSectionProvider {
    private final ProfileSectionRegistry sections;
    private final TomlXcoreConfig config;
    private final PlayerDataRepository players;
    private ProfileSectionRegistry.Registration registration;

    @Inject
    public LegacyHexedProfileSection(ProfileSectionRegistry sections, TomlXcoreConfig config, PlayerDataRepository players) {
        this.sections = sections;
        this.config = config;
        this.players = players;
    }

    @PostConstruct
    public void start() {
        registration = sections.register(this);
    }

    @PreDestroy
    public void stop() {
        if (registration != null) registration.close();
    }

    @Override
    public String id() {
        return "legacy-hexed";
    }

    /** Blocking: reads the leaderboard place. */
    @Override
    public Optional<ProfileSectionView> load(PlayerData target) {
        boolean hexedServer = config.server.name.toLowerCase(Locale.ROOT).contains("hexed");
        if (target == null || target.hexedPoints <= 0 && !hexedServer) return Optional.empty();
        var rank = target.hexedRank();
        int points = target.hexedPoints;
        Integer place = players.findTopRank(TopCategory.HEXED, target);
        return Optional.of(local -> {
            String headline = local.t("player-stats-hexed-rank") + " " + local.t("hexed-ranks-" + rank.name());
            String progress = rank.hasNext()
                    ? local.t("player-stats-hexed-wins-left", args("wins", Math.max(0, rank.next.requirements.wins() - points),
                            "rank", local.t("hexed-ranks-" + rank.next.name())))
                    : local.t("player-stats-max-rank");
            return new ProfileSection(headline, List.of(
                    local.t("player-stats-hexed-points", args("points", points)),
                    local.t("player-stats-hexed-leaderboard") + " [accent]" + (place != null ? "#" + place : "-") + "[]"),
                    List.of(progress));
        });
    }
}
