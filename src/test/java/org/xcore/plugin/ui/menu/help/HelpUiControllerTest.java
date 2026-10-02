package org.xcore.plugin.ui.menu.help;

import com.ospx.flubundle.Bundle;
import mindustry.ui.builder.MenuResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.xcore.plugin.config.TomlSecretsConfig;
import org.xcore.plugin.database.repository.PlayerDataRepository;
import org.xcore.plugin.localization.Localization;
import org.xcore.plugin.model.PlayerData;
import org.xcore.plugin.session.Session;
import mindustry.ui.builder.UiDslWriter;
import org.xcore.ui.VNode;
import org.xcore.ui.VNodeCompiler;
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

    @Test
    @DisplayName("render compiles list view with 740 width desktop metrics, category tabs, and command cards")
    void render_compilesListViewDesktop() {
        Session session = createTestSession(false);
        HelpUiController controller = new HelpUiController(session, null);
        HelpUiModel model = new HelpUiModel(HelpUiModel.ViewMode.LIST, HelpCategory.ALL, sampleCommands(), null, false);

        VNode root = controller.render(model);
        VNodeCompiler compiler = new VNodeCompiler((key, args) -> session.locale().format(key, args));
        String dsl = UiDslWriter.write(compiler.compile(root));

        assertThat(dsl).contains("background: pane");
        assertThat(dsl).contains("width: 740");
        assertThat(dsl).contains("maxHeight: 480");

        // Header and tabs
        assertThat(dsl).contains("help-ui-title");
        assertThat(dsl).contains("action:tab:all");
        assertThat(dsl).contains("action:tab:game");
        assertThat(dsl).contains("action:tab:votes");
        assertThat(dsl).contains("action:tab:admin");

        // Cards
        assertThat(dsl).contains("action:cmd:hub");
        assertThat(dsl).contains("action:cmd:votekick");
        assertThat(dsl).contains("action:cmd:ban");

        // Overloads badge
        assertThat(dsl).contains("help-ui-overloads");
    }

    @Test
    @DisplayName("render compiles responsive mobile layout when isMobile is true")
    void render_compilesMobileLayout() {
        Session session = createTestSession(true);
        HelpUiController controller = new HelpUiController(session, null);
        HelpUiModel model = new HelpUiModel(HelpUiModel.ViewMode.LIST, HelpCategory.ALL, sampleCommands(), null, true);

        VNode root = controller.render(model);
        VNodeCompiler compiler = new VNodeCompiler((key, args) -> session.locale().format(key, args));
        String dsl = UiDslWriter.write(compiler.compile(root));

        assertThat(dsl).contains("background: pane");
        assertThat(dsl).contains("width: 680");
        assertThat(dsl).contains("maxHeight: 440");
        assertThat(dsl).contains("size: 32");
        assertThat(dsl).contains("action:cmd:hub");
    }

    @Test
    @DisplayName("render omits Admin tab when no admin commands are present in model")
    void render_omitsAdminTabWhenNoAdminCommands() {
        Session session = createTestSession(false);
        HelpUiController controller = new HelpUiController(session, null);
        // Only non-admin commands
        List<HelpCommandItem> regularOnly = sampleCommands().stream()
                .filter(c -> c.category() != HelpCategory.ADMIN)
                .toList();
        HelpUiModel model = new HelpUiModel(HelpUiModel.ViewMode.LIST, HelpCategory.ALL, regularOnly, null, false);

        VNode root = controller.render(model);
        VNodeCompiler compiler = new VNodeCompiler((key, args) -> session.locale().format(key, args));
        String dsl = UiDslWriter.write(compiler.compile(root));

        assertThat(dsl).contains("action:tab:all");
        assertThat(dsl).contains("action:tab:game");
        assertThat(dsl).contains("action:tab:votes");
        assertThat(dsl).doesNotContain("action:tab:admin");
    }

    @Test
    @DisplayName("escapeMarkup escapes opening brackets so Arc does not treat them as color tags")
    void escapeMarkup_escapesOpeningBrackets() {
        assertThat(HelpUiController.escapeMarkup("votekick <player> [reason]"))
                .isEqualTo("votekick <player> [[reason]");
        assertThat(HelpUiController.escapeMarkup(null)).isEqualTo("");
    }

    @Test
    @DisplayName("SelectCategory updates category and rerenders")
    void selectCategory_updatesCategoryAndRerenders() {
        Session session = createTestSession(false);
        HelpUiController controller = new HelpUiController(session, null);
        HelpUiModel model = new HelpUiModel(HelpUiModel.ViewMode.LIST, HelpCategory.ALL, sampleCommands(), null, false);

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
        HelpUiModel model = new HelpUiModel(HelpUiModel.ViewMode.LIST, HelpCategory.ALL, sampleCommands(), null, false);

        ControllerContext ctx = mock(ControllerContext.class);
        UpdateResult<HelpUiModel> result = controller.update(
                model, new HelpUiEvent.SelectCommand("hub"), ctx
        );

        assertThat(result.fullRerender()).isTrue();
        assertThat(result.model().mode()).isEqualTo(HelpUiModel.ViewMode.DETAILS);
        assertThat(result.model().selectedCommandName()).isEqualTo("hub");

        VNode detailsNode = controller.render(result.model());
        VNodeCompiler compiler = new VNodeCompiler((key, args) -> session.locale().format(key, args));
        String dsl = UiDslWriter.write(compiler.compile(detailsNode));

        // Details elements
        assertThat(dsl).contains("action:back");
        assertThat(dsl).contains("help-ui-back");
        assertThat(dsl).contains("help-ui-aliases");
        assertThat(dsl).contains("help-ui-syntax-title");
        assertThat(dsl).contains("action:run:hub");
        assertThat(dsl).contains("action:copy:hub");
    }

    @Test
    @DisplayName("BackToList switches model back to LIST mode")
    void backToList_switchesToListMode() {
        Session session = createTestSession(false);
        HelpUiController controller = new HelpUiController(session, null);
        HelpUiModel model = new HelpUiModel(HelpUiModel.ViewMode.DETAILS, HelpCategory.GAME, sampleCommands(), "hub", false);

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

        assertThat(controller.parseEvent(new MenuResult("action:cmd:votekick")))
                .isEqualTo(new HelpUiEvent.SelectCommand("votekick"));

        assertThat(controller.parseEvent(new MenuResult("action:run:hub")))
                .isEqualTo(new HelpUiEvent.ExecuteCommand("hub"));

        assertThat(controller.parseEvent(new MenuResult("action:copy:hub")))
                .isEqualTo(new HelpUiEvent.CopyCommand("hub"));
    }

    @Test
    @DisplayName("formatCardBottomLine properly constrains long syntax and description to prevent overflow")
    void formatCardBottomLine_constrainsLength() {
        String alertLine = HelpUiController.formatCardBottomLine(
                "alert <targets> <message>",
                "Displays a prominent announcement banner to target players or all players.",
                48
        );
        String strippedAlert = arc.util.Strings.stripColors(alertLine);
        assertThat(strippedAlert.length()).isLessThanOrEqualTo(48);
        assertThat(strippedAlert).startsWith("/alert <targets> <message>");
        assertThat(strippedAlert).contains("|");
        assertThat(strippedAlert).endsWith("...");

        String avnwLine = HelpUiController.formatCardBottomLine(
                "avnw",
                "Примусово достроково запустити наступну хвилю. [scarlet]Тільки для адміністраторів.",
                48
        );
        String strippedAvnw = arc.util.Strings.stripColors(avnwLine);
        assertThat(strippedAvnw.length()).isLessThanOrEqualTo(48);
        assertThat(strippedAvnw).startsWith("/avnw");
        assertThat(strippedAvnw).contains("|");
        assertThat(strippedAvnw).doesNotContain("Тільки для адміністраторів");

        String longSyntaxLine = HelpUiController.formatCardBottomLine(
                "votekick <player_name_or_id> [reason_text...]",
                "Vote to kick a player from the server",
                48
        );
        String strippedLong = arc.util.Strings.stripColors(longSyntaxLine);
        assertThat(strippedLong.length()).isLessThanOrEqualTo(48);
        assertThat(strippedLong).contains("|");
    }
}
