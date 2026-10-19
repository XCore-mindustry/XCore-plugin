package org.xcore.plugin.ui.menu.help;

import org.xcore.plugin.ui.kit.TextWidth;
import com.ospx.flubundle.Bundle;
import mindustry.ui.builder.MenuResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.xcore.plugin.config.TomlSecretsConfig;
import org.xcore.plugin.database.repository.PlayerDataRepository;
import org.xcore.plugin.localization.Localization;
import org.xcore.plugin.model.PlayerData;
import org.xcore.plugin.session.Session;
import org.xcore.plugin.ui.kit.LayoutAssert;
import org.xcore.plugin.ui.kit.Screen;
import org.xcore.ui.VNode;
import org.xcore.ui.runtime.ControllerContext;
import org.xcore.ui.runtime.UpdateResult;

import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class HelpUiControllerTest {

    private Session createTestSession(boolean isMobile) {
        mindustry.gen.Player player = mindustry.gen.Player.create();
        mindustry.net.NetConnection con = mock(mindustry.net.NetConnection.class);
        con.mobile = isMobile;
        player.con = con;

        PlayerData data = new PlayerData("viewer-1", true);
        data.uuid = "viewer-1";

        Session session = new Session(
                new TomlSecretsConfig(),
                mock(Bundle.class),
                null,
                mock(PlayerDataRepository.class),
                player,
                data
        );

        Localization localization = mock(Localization.class);
        when(localization.t(org.mockito.ArgumentMatchers.anyString())).thenAnswer(inv -> inv.getArgument(0));
        when(localization.t(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyMap())).thenAnswer(inv -> inv.getArgument(0));
        when(localization.format(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyMap())).thenAnswer(inv -> inv.getArgument(0));
        when(localization.getLocale()).thenReturn(Locale.US);
        session.localization = localization;

        return session;
    }

    private List<HelpCommandItem> sampleCommands() {
        return List.of(
                new HelpCommandItem(
                        "hub",
                        HelpCategory.GAME,
                        "hub",
                        List.of("hub", "hub <lobby>"),
                        List.of("servers"),
                        "Connect to main lobby or choose server",
                        List.of(new HelpCommandItem.ArgumentInfo("lobby", false, "Optional lobby name")),
                        false,
                        true
                ),
                new HelpCommandItem(
                        "votekick",
                        HelpCategory.VOTES,
                        "votekick <player> [reason]",
                        List.of("votekick <player> [reason]"),
                        List.of("vk"),
                        "Start a votekick against a player",
                        List.of(
                                new HelpCommandItem.ArgumentInfo("player", true, "Target player name or ID"),
                                new HelpCommandItem.ArgumentInfo("reason", false, "Reason for kick")
                        ),
                        false,
                        false
                ),
                new HelpCommandItem(
                        "ban",
                        HelpCategory.ADMIN,
                        "ban <id> <period> [reason]",
                        List.of("ban <id> <period> [reason]"),
                        List.of(),
                        "Ban a player",
                        List.of(
                                new HelpCommandItem.ArgumentInfo("id", true, "Player ID"),
                                new HelpCommandItem.ArgumentInfo("period", true, "Ban duration"),
                                new HelpCommandItem.ArgumentInfo("reason", false, "Ban reason")
                        ),
                        true,
                        false
                )
        );
    }

    /** Enough commands for several pages, with the long syntaxes and descriptions real ones have. */
    private List<HelpCommandItem> manyCommands() {
        List<HelpCommandItem> commands = new java.util.ArrayList<>(sampleCommands());
        HelpCategory[] categories = {HelpCategory.GENERAL, HelpCategory.GAME, HelpCategory.SOCIAL, HelpCategory.VOTES, HelpCategory.ADMIN};
        for (int i = 0; i < 40; i++) {
            HelpCategory category = categories[i % categories.length];
            commands.add(new HelpCommandItem(
                    "command" + i,
                    category,
                    "command" + i + " <player_name_or_id> [reason_text...]",
                    List.of("command" + i + " <player_name_or_id> [reason_text...]", "command" + i + " list"),
                    List.of("c" + i, "cmd" + i),
                    "Показывает заметное объявление выбранным игрокам или всем игрокам на сервере сразу. [scarlet]Только для админов.",
                    List.of(
                            new HelpCommandItem.ArgumentInfo("player_name_or_id", true, "Ник игрока или его идентификатор, как в списке игроков"),
                            new HelpCommandItem.ArgumentInfo("reason_text", false, "Причина, которую увидят остальные игроки")
                    ),
                    category == HelpCategory.ADMIN,
                    false
            ));
        }
        return commands;
    }

    private Session realSession(String language) {
        Session session = createTestSession(false);
        session.localization = LayoutAssert.localization(language);
        return session;
    }

    @Test
    @DisplayName("the list shows a page of commands as rows, with a tab per category that has any")
    void window_listsAPageOfCommands() {
        Session session = createTestSession(false);
        HelpUiController controller = new HelpUiController(session, null);
        HelpUiModel model = new HelpUiModel(HelpUiModel.ViewMode.LIST, HelpCategory.ALL, sampleCommands(), null);

        for (Screen screen : Screen.ALL) {
            VNode window = controller.window(model, screen);
            assertThat(LayoutAssert.actions(window)).containsExactly(
                    "action:tab:all", "action:tab:general", "action:tab:game", "action:tab:social",
                    "action:tab:votes", "action:tab:admin",
                    "action:search", "action:clear_search",
                    "action:cmd:hub", "action:cmd:votekick", "action:cmd:ban");
            assertThat(LayoutAssert.allText(window)).contains("help-ui-title", "help-ui-overloads");
        }
        String dsl = LayoutAssert.dsl(controller.render(model));
        assertThat(dsl).contains("background: pane", "condition: \"width >= 800\"", "condition: \"width < 490\"");
        assertThat(dsl).doesNotContain("maxWidth", "wrap: true\n    table", "action:close");
    }

    @Test
    @DisplayName("render omits Admin tab when no admin commands are present in model")
    void render_omitsAdminTabWhenNoAdminCommands() {
        Session session = createTestSession(false);
        HelpUiController controller = new HelpUiController(session, null);
        List<HelpCommandItem> regularOnly = sampleCommands().stream()
                .filter(c -> c.category() != HelpCategory.ADMIN)
                .toList();
        HelpUiModel model = new HelpUiModel(HelpUiModel.ViewMode.LIST, HelpCategory.ALL, regularOnly, null);

        List<String> actions = LayoutAssert.actions(controller.window(model, Screen.SMALL));

        assertThat(actions).contains("action:tab:all", "action:tab:game", "action:tab:votes");
        assertThat(actions).doesNotContain("action:tab:admin");
    }

    @Test
    @DisplayName("the list and a command's page are laid out for every screen in every language")
    void window_isLaidOutForEveryScreen() {
        for (String language : LayoutAssert.LANGUAGES) {
            Session session = realSession(language);
            HelpUiController controller = new HelpUiController(session, null);
            for (HelpCategory category : HelpCategory.values()) {
                HelpUiModel list = new HelpUiModel(HelpUiModel.ViewMode.LIST, category, manyCommands(), null);
                for (Screen screen : Screen.ALL) {
                    VNode window = controller.window(list, screen);
                    LayoutAssert.assertLaidOut(window, screen);
                    assertThat(LayoutAssert.allText(window)).doesNotContain("help-ui-", "help-cat-");
                }
                LayoutAssert.assertFitsPacket(controller.render(list), language + " " + category);
            }
            for (String name : List.of("hub", "votekick", "ban", "command4")) {
                HelpUiModel details = new HelpUiModel(HelpUiModel.ViewMode.DETAILS, HelpCategory.ALL, manyCommands(), name);
                for (Screen screen : Screen.ALL) {
                    VNode window = controller.window(details, screen);
                    LayoutAssert.assertLaidOut(window, screen);
                    assertThat(LayoutAssert.allText(window)).doesNotContain("help-ui-", "help-cat-");
                }
                LayoutAssert.assertFitsPacket(controller.render(details), language + " /" + name);
            }
        }
    }

    @Test
    void search_matchesNamesAliasesAndDescriptionsAndPreservesQuery() {
        HelpUiModel model = new HelpUiModel(HelpUiModel.ViewMode.LIST, HelpCategory.ALL, sampleCommands(), null);
        assertThat(model.withSearch(" /HUB ").filteredCommands()).extracting(HelpCommandItem::name).containsExactly("hub");
        assertThat(model.withSearch("SERVERS").filteredCommands()).extracting(HelpCommandItem::name).containsExactly("hub");
        assertThat(model.withSearch("ban a player").filteredCommands()).extracting(HelpCommandItem::name).containsExactly("ban");
        assertThat(model.withSearch("servers").withCategory(HelpCategory.ADMIN).filteredCommands()).isEmpty();
        assertThat(model.withSearch("servers").withDetails("hub").withList().searchQuery()).isEqualTo("servers");
        assertThat(model.withSearch("missing").withPage(99).page()).isZero();
        assertThat(model.withSearch("missing").withSearch("").filteredCommands()).hasSize(3);
    }

    @Test
    void search_patchesSlotsResetsPageAndShowsLocalizedEmptyState() {
        HelpUiController controller = new HelpUiController(realSession("ru"), null);
        HelpUiModel model = new HelpUiModel(HelpUiModel.ViewMode.LIST, HelpCategory.ALL, manyCommands(), null, 3);
        MenuResult wire = new MenuResult("action:search");
        wire.values = arc.struct.ObjectMap.of("field_help_search", "servers");
        assertThat(controller.parseEvent(wire)).isEqualTo(new HelpUiEvent.Search("servers"));
        assertThat(controller.parseEvent(new MenuResult("action:search"))).isEqualTo(new HelpUiEvent.Search(""));
        assertThat(controller.parseEvent(new MenuResult("action:clear_search"))).isEqualTo(new HelpUiEvent.Search(""));
        var result = controller.update(model, controller.parseEvent(wire), mock(ControllerContext.class));
        assertThat(result.fullRerender()).isFalse();
        assertThat(result.model().page()).isZero();
        assertThat(result.model().pageCommands()).extracting(HelpCommandItem::name).containsExactly("hub");
        assertThat(result.dirtySlots()).containsExactlyElementsOf(
                Screen.slots(HelpUiController.SLOT_SEARCH, HelpUiController.SLOT_COMMANDS, HelpUiController.SLOT_PAGER));
        for (String language : LayoutAssert.LANGUAGES) {
            var localized = new HelpUiController(realSession(language), null);
            for (Screen screen : Screen.ALL) {
                var window = localized.window(model.withSearch("no matches"), screen);
                LayoutAssert.assertLaidOut(window, screen);
                assertThat(LayoutAssert.allText(window)).doesNotContain("help-ui-search-empty");
            }
        }
    }

    @Test
    @DisplayName("a long list is split into pages; turning one patches the list and the pager only")
    void openPage_patchesListAndPager() {
        Session session = realSession("ru");
        HelpUiController controller = new HelpUiController(session, null);
        HelpUiModel model = new HelpUiModel(HelpUiModel.ViewMode.LIST, HelpCategory.ALL, manyCommands(), null);
        assertThat(model.pages()).isEqualTo(4);
        assertThat(model.pageCommands()).hasSize(HelpUiModel.PAGE_SIZE);
        assertThat(LayoutAssert.actions(controller.window(model, Screen.SMALL))).contains("action:page:1").doesNotContain("action:page:0");

        UpdateResult<HelpUiModel> result = controller.update(model, new HelpUiEvent.OpenPage(3), mock(ControllerContext.class));

        assertThat(result.fullRerender()).isFalse();
        assertThat(result.dirtySlots()).containsExactlyElementsOf(
                Screen.slots(HelpUiController.SLOT_COMMANDS, HelpUiController.SLOT_PAGER));
        assertThat(result.model().page()).isEqualTo(3);
        assertThat(result.model().pageCommands()).hasSize(43 - 3 * HelpUiModel.PAGE_SIZE);
        VNode rendered = controller.render(result.model());
        for (var slot : result.dirtySlots()) {
            assertThat(org.xcore.ui.VNodes.findSlot(rendered, slot.path())).as(slot.path()).isNotNull();
        }
        assertThat(LayoutAssert.actions(controller.window(result.model(), Screen.SMALL)))
                .contains("action:page:2").doesNotContain("action:page:4");

        // A page past the end is the last one, and a new category starts from its first.
        assertThat(model.withPage(99).page()).isEqualTo(3);
        assertThat(result.model().withCategory(HelpCategory.GAME).page()).isZero();
        // Going into a command and back returns to the page it was picked on.
        assertThat(result.model().withDetails("command39").withList().page()).isEqualTo(3);
    }

    @Test
    @DisplayName("a row shows the command as typed and its description, each cut to the row")
    void rowText_fitsTheRow() {
        HelpCommandItem alert = new HelpCommandItem("alert", HelpCategory.ADMIN, "alert <targets> <message>",
                List.of("alert <targets> <message>"), List.of(),
                "Примусово достроково запустити наступну хвилю та показати оголошення. [scarlet]Тільки для адміністраторів.",
                List.of(), true, false);

        String text = HelpUiController.rowText(alert, 340f, null);

        String[] lines = text.split("\n");
        assertThat(lines).hasSize(2);
        assertThat(lines[0]).contains("[accent]/alert[]", "<targets> <message>");
        assertThat(lines[1]).endsWith("…[]").doesNotContain("Тільки для адміністраторів");
        assertThat(org.xcore.plugin.ui.kit.TextWidth.of(text)).isLessThanOrEqualTo(340f);
        // A row too narrow for the command itself cuts that too.
        assertThat(HelpUiController.rowText(alert, 150f, null).split("\n")[0]).contains("…");

        // An optional argument keeps its bracket: it is escaped, not read as a colour.
        HelpCommandItem kick = sampleCommands().get(1);
        assertThat(HelpUiController.rowText(kick, 600f, null)).contains("<player> [[reason]");
    }

    @Test
    @DisplayName("SelectCategory updates category and rerenders")
    void selectCategory_updatesCategoryAndRerenders() {
        Session session = createTestSession(false);
        HelpUiController controller = new HelpUiController(session, null);
        HelpUiModel model = new HelpUiModel(HelpUiModel.ViewMode.LIST, HelpCategory.ALL, sampleCommands(), null);

        ControllerContext ctx = mock(ControllerContext.class);
        UpdateResult<HelpUiModel> result = controller.update(
                model, new HelpUiEvent.SelectCategory(HelpCategory.VOTES), ctx
        );

        assertThat(result.fullRerender()).isTrue();
        assertThat(result.model().selectedCategory()).isEqualTo(HelpCategory.VOTES);
        assertThat(result.model().filteredCommands()).extracting(HelpCommandItem::name).containsExactly("votekick");
    }

    @Test
    @DisplayName("SelectCommand switches to DETAILS mode and renders rich details view")
    void selectCommand_switchesToDetailsModeAndRenders() {
        Session session = createTestSession(false);
        HelpUiController controller = new HelpUiController(session, null);
        HelpUiModel model = new HelpUiModel(HelpUiModel.ViewMode.LIST, HelpCategory.ALL, sampleCommands(), null);

        ControllerContext ctx = mock(ControllerContext.class);
        UpdateResult<HelpUiModel> result = controller.update(
                model, new HelpUiEvent.SelectCommand("hub"), ctx
        );

        assertThat(result.fullRerender()).isTrue();
        assertThat(result.model().mode()).isEqualTo(HelpUiModel.ViewMode.DETAILS);
        assertThat(result.model().selectedCommandName()).isEqualTo("hub");

        for (Screen screen : Screen.ALL) {
            VNode details = controller.window(result.model(), screen);
            assertThat(LayoutAssert.actions(details)).containsExactly("action:back", "action:run:hub", "action:copy:hub");
            assertThat(LayoutAssert.allText(details)).contains(
                    "help-ui-btn-back", "help-ui-aliases", "help-ui-syntax-title", "help-ui-args-title", "/hub <lobby>");
        }
    }

    @Test
    @DisplayName("BackToList switches model back to LIST mode")
    void backToList_switchesToListMode() {
        Session session = createTestSession(false);
        HelpUiController controller = new HelpUiController(session, null);
        HelpUiModel model = new HelpUiModel(HelpUiModel.ViewMode.DETAILS, HelpCategory.GAME, sampleCommands(), "hub");

        ControllerContext ctx = mock(ControllerContext.class);
        UpdateResult<HelpUiModel> result = controller.update(
                model, new HelpUiEvent.BackToList(), ctx
        );

        assertThat(result.fullRerender()).isTrue();
        assertThat(result.model().mode()).isEqualTo(HelpUiModel.ViewMode.LIST);
        assertThat(result.model().selectedCommandName()).isNull();
    }

    @Test
    @DisplayName("parseEvent correctly maps wire actions to events")
    void parseEvent_mapsWireActions() {
        HelpUiController controller = new HelpUiController(null, null);

        assertThat(controller.parseEvent(new MenuResult("action:close")))
                .isEqualTo(new HelpUiEvent.Close());

        assertThat(controller.parseEvent(new MenuResult((String) null)))
                .isEqualTo(new HelpUiEvent.Close());

        assertThat(controller.parseEvent(new MenuResult("action:back")))
                .isEqualTo(new HelpUiEvent.BackToList());

        assertThat(controller.parseEvent(new MenuResult("action:tab:votes")))
                .isEqualTo(new HelpUiEvent.SelectCategory(HelpCategory.VOTES));

        assertThat(controller.parseEvent(new MenuResult("action:page:2")))
                .isEqualTo(new HelpUiEvent.OpenPage(2));

        assertThat(controller.parseEvent(new MenuResult("action:cmd:votekick")))
                .isEqualTo(new HelpUiEvent.SelectCommand("votekick"));

        assertThat(controller.parseEvent(new MenuResult("action:run:hub")))
                .isEqualTo(new HelpUiEvent.ExecuteCommand("hub"));

        assertThat(controller.parseEvent(new MenuResult("action:copy:hub")))
                .isEqualTo(new HelpUiEvent.CopyCommand("hub"));
    }
}
