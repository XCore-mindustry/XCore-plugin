package org.xcore.plugin.rating.view;

import org.jspecify.annotations.Nullable;
import org.xcore.plugin.integration.profile.ProfileSection;
import org.xcore.plugin.integration.profile.ProfileSectionProvider;
import org.xcore.plugin.integration.profile.ProfileSectionView;
import org.xcore.plugin.localization.Localization;
import org.xcore.plugin.model.PlayerData;
import org.xcore.plugin.rating.ladder.Ladder;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * A ladder's block in the player profile: this season's rating, league, rank and record,
 * the time the season has left, and how the player finished the seasons before.
 */
public final class LadderProfileSection implements ProfileSectionProvider {

    /** A mode-specific fact appended to the section's details, e.g. a rating from before the ladder. */
    @FunctionalInterface
    public interface Detail {
        Optional<String> render(PlayerData target, Localization local);
    }

    private final LadderViews views;
    private final Ladder ladder;
    private final String icon;
    private final int priority;
    private final boolean showUnplayed;
    @Nullable
    private final Detail extra;
    private final LadderProgressText text;

    LadderProfileSection(LadderViews views, Ladder ladder, String icon, int priority, boolean showUnplayed) {
        this(views, ladder, icon, priority, showUnplayed, null);
    }

    private LadderProfileSection(LadderViews views, Ladder ladder, String icon, int priority, boolean showUnplayed,
                                 @Nullable Detail extra) {
        this.views = Objects.requireNonNull(views, "views");
        this.ladder = Objects.requireNonNull(ladder, "ladder");
        this.icon = icon == null ? "" : icon;
        this.priority = priority;
        this.showUnplayed = showUnplayed;
        this.extra = extra;
        this.text = new LadderProgressText(views.text());
    }

    public LadderProfileSection withDetail(Detail detail) {
        return new LadderProfileSection(views, ladder, icon, priority, showUnplayed,
                Objects.requireNonNull(detail, "detail"));
    }

    @Override
    public String id() {
        return "ladder:" + ladder.id();
    }

    @Override
    public int priority() {
        return priority;
    }

    @Override
    public Optional<ProfileSectionView> load(PlayerData target) {
        if (target == null || target.uuid == null || target.uuid.isBlank()) {
            return Optional.empty();
        }
        LadderProgress progress = views.progress(ladder, target.uuid);
        if (!showUnplayed && !progress.everPlayed()) {
            return Optional.empty();
        }
        return Optional.of(local -> render(progress, target, local));
    }

    private ProfileSection render(LadderProgress progress, PlayerData target, Localization local) {
        List<String> details = new ArrayList<>(text.details(progress, local));
        if (extra != null) {
            extra.render(target, local).ifPresent(details::add);
        }
        List<String> lines = new ArrayList<>();
        lines.add(text.leagueProgress(progress, local));
        lines.addAll(text.seasonLine(progress, local));
        lines.addAll(text.history(progress, local));
        return new ProfileSection(text.headline(icon, progress, local), details, lines);
    }
}
