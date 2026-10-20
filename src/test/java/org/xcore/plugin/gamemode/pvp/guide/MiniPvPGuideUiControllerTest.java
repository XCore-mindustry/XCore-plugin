package org.xcore.plugin.gamemode.pvp.guide;

import mindustry.game.Team;
import mindustry.ui.builder.MenuResult;
import org.junit.jupiter.api.Test;
import org.xcore.plugin.gamemode.pvp.guide.MiniPvPGuideUiController.GuideEvent;
import org.xcore.plugin.gamemode.pvp.guide.MiniPvPGuideUiController.GuideModel;
import org.xcore.plugin.gamemode.pvp.guide.MiniPvPGuideUiController.Tab;
import org.xcore.plugin.gamemode.pvp.guide.MiniPvPModeFacts.ContentRef;
import org.xcore.plugin.gamemode.pvp.guide.MiniPvPModeFacts.Multiplier;
import org.xcore.plugin.gamemode.pvp.guide.MiniPvPModeFacts.Rating;
import org.xcore.plugin.gamemode.pvp.guide.MiniPvPModeFacts.TeamCount;
import org.xcore.plugin.ui.kit.LayoutAssert;
import org.xcore.plugin.ui.kit.Screen;
import org.xcore.ui.VNode;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MiniPvPGuideUiControllerTest {

    private static final Rating RATING = new Rating(1000, 2, 15, 15, 50, 25);

    /** A round in progress on a map that changes a lot: every card of the rules tab has something to say. */
    private static MiniPvPModeFacts busyFacts() {
        return new MiniPvPModeFacts("Glacier", 754,
                List.of(new TeamCount(Team.sharded.id, 4), new TeamCount(Team.crux.id, 3),
                        new TeamCount(Team.malis.id, 1)),
                40, true, 50, false,
                List.of(new Multiplier("unit-damage", 150), new Multiplier("unit-health", 80),
                        new Multiplier("unit-cost", 125), new Multiplier("unit-build-speed", 200),
                        new Multiplier("block-damage", 50), new Multiplier("block-health", 300),
                        new Multiplier("build-cost", 75), new Multiplier("build-speed", 150)),
                List.of(new ContentRef("block-foreshadow-ui"), new ContentRef("block-ripple-ui"),
                        new ContentRef("block-spectre-ui"), new ContentRef("block-meltdown-ui"),
                        new ContentRef("block-thorium-reactor-ui"), new ContentRef("block-impact-reactor-ui")),
                List.of(new ContentRef("unit-quad-ui"), new ContentRef("unit-oct-ui")),
                true, true, false, RATING);
    }

    /** Between rounds, on a map with vanilla rules. */
    private static MiniPvPModeFacts vanillaFacts() {
        return new MiniPvPModeFacts(null, 0, List.of(), 0, true, 0, false, List.of(), List.of(), List.of(),
                false, false, true, RATING);
    }

    private static MiniPvPGuideUiController controller(String language) {
        return new MiniPvPGuideUiController(LayoutAssert.localization(language));
    }

    @Test
    void everyTabIsLaidOutForEveryScreenAndTranslated() {
        for (String language : LayoutAssert.LANGUAGES) {
            MiniPvPGuideUiController controller = controller(language);
            for (MiniPvPModeFacts facts : List.of(busyFacts(), vanillaFacts())) {
                for (Tab tab : Tab.values()) {
                    GuideModel model = new GuideModel(tab, facts);
                    for (Screen screen : Screen.ALL) {
                        VNode window = controller.window(model, screen);
                        LayoutAssert.assertLaidOut(window, screen);
                        assertThat(LayoutAssert.allText(window))
                                .as("%s in %s", tab, language)
                                .doesNotContain("pvp-guide-")
                                .doesNotContain("{ $")
                                .doesNotContain("{$");
                    }
                    LayoutAssert.assertFitsPacket(controller.render(model), tab + " in " + language);
                }
            }
        }
    }

    @Test
    void theWindowOnlySwitchesTabs() {
        VNode window = controller("ru").window(new GuideModel(Tab.RULES, busyFacts()), Screen.WIDE);

        // The dialog of the client closes the guide; the window itself only switches tabs.
        assertThat(LayoutAssert.actions(window))
                .containsExactly("action:tab:round", "action:tab:rules", "action:tab:rating");
    }

    @Test
    void theRoundTabShowsTheRoundAsItStands() {
        String text = LayoutAssert.allText(controller("ru").render(new GuideModel(Tab.ROUND, busyFacts())));

        assertThat(text).contains("Glacier", "12", "4 игрока", "1 игрок", "/pvp info", "/matches");
    }

    @Test
    void theRulesTabShowsWhatDiffersFromVanilla() {
        String text = LayoutAssert.allText(controller("ru").render(new GuideModel(Tab.RULES, busyFacts())));

        assertThat(text).contains("Урон юнитов: [accent]150%", "Прочность построек: [accent]300%",
                "[accent]50[] тайлов", "[accent]40[]", "Туман войны", "Реакторы не взрываются");
        assertThat(text).doesNotContain("Обычные правила");
    }

    @Test
    void aVanillaMapSaysSoInsteadOfListingNothing() {
        String text = LayoutAssert.allText(controller("ru").render(new GuideModel(Tab.RULES, vanillaFacts())));

        assertThat(text).contains("Обычные правила");
        assertThat(text).doesNotContain("Туман", "Лимит юнитов", "Другой баланс", "Запрещённые");
    }

    @Test
    void theRatingTabNamesTheThresholds() {
        String text = LayoutAssert.allText(controller("en").render(new GuideModel(Tab.RATING, busyFacts())));

        assertThat(text).contains("[accent]1000[]", "[accent]15[] s", "[accent]2[] rated players",
                "[accent]50%[]", "[accent]25%[]");
    }

    @Test
    void tabButtonsSwitchTabsAndEverythingElseCloses() {
        MiniPvPGuideUiController controller = controller("en");
        GuideModel model = new GuideModel(Tab.ROUND, busyFacts());

        GuideEvent select = controller.parseEvent(new MenuResult("action:tab:rating"));
        assertThat(select).isEqualTo(new GuideEvent.SelectTab(Tab.RATING));
        assertThat(controller.update(model, select, null).model().tab()).isEqualTo(Tab.RATING);

        assertThat(controller.parseEvent(new MenuResult("action:close"))).isInstanceOf(GuideEvent.Close.class);
        assertThat(controller.parseEvent(null)).isInstanceOf(GuideEvent.Close.class);
    }
}
