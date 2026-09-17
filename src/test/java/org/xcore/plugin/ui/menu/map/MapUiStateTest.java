package org.xcore.plugin.ui.menu.map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.xcore.plugin.model.MapData;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MapUiStateTest {

    @Test
    @DisplayName("Converts between discriminated MapUiState union and legacy MapUiModel bidirectionally")
    void convertsBetweenDiscriminatedUnionAndModel() {
        var summary = new MapUiState.MapSummary("arena.msav", "Arena", "Author", 100, 100, 10, 2, false);
        var browserState = new MapUiState.Browser(
                "test-uuid", false, "arena", 1, 5, List.of(summary), 10, false
        );

        MapUiModel modelFromBrowser = browserState.toModel();
        assertThat(modelFromBrowser.mode()).isEqualTo(MapUiModel.ViewMode.BROWSER);
        assertThat(modelFromBrowser.searchQuery()).isEqualTo("arena");
        assertThat(modelFromBrowser.displayedMaps()).hasSize(1);

        MapUiState roundTrippedBrowser = modelFromBrowser.toState();
        assertThat(roundTrippedBrowser).isInstanceOf(MapUiState.Browser.class);
        assertThat(((MapUiState.Browser) roundTrippedBrowser).searchQuery()).isEqualTo("arena");

        var detailsState = new MapUiState.Details(
                "test-uuid", true, "arena.msav",
                new MapUiState.MapIdentity("Arena", "Author", "Desc", 100, 100, "survival", true),
                new MapUiState.TelemetryMatrix(50, 20, "5m", "1m", "3m", "10m", 15, 30.0, 10.0),
                new MapUiState.ReputationState(true, 10, 2, 83, false),
                new MapUiState.PreviewState(false, "region_1", false),
                new MapUiState.RtvState(true, 3, 5, 20),
                new MapUiState.AdminState(true, false, 0L),
                new MapData("Arena", "arena.msav", "Author", "survival")
        );

        MapUiModel modelFromDetails = detailsState.toModel();
        assertThat(modelFromDetails.mode()).isEqualTo(MapUiModel.ViewMode.DETAILS);
        assertThat(modelFromDetails.mapName()).isEqualTo("Arena");
        assertThat(modelFromDetails.approvalRatePercent()).isEqualTo(83);
        assertThat(modelFromDetails.resolvedDetails()).isNotNull();

        MapUiState roundTrippedDetails = modelFromDetails.toState();
        assertThat(roundTrippedDetails).isInstanceOf(MapUiState.Details.class);
        assertThat(((MapUiState.Details) roundTrippedDetails).telemetry().plays()).isEqualTo(50);
    }

    @Test
    @DisplayName("MapUiCmd represents all side-effects as discriminated sealed types")
    void mapUiCmdRepresentsAllSideEffects() {
        MapUiCmd loadCmd = new MapUiCmd.LoadMapDetails("arena.msav");
        MapUiCmd voteCmd = new MapUiCmd.PersistReputationVote("arena.msav", true, false);
        MapUiCmd rtvCmd = new MapUiCmd.SubscribeRtv("arena.msav", 12345L);
        MapUiCmd closeCmd = new MapUiCmd.CloseSession();

        assertThat(loadCmd).isInstanceOf(MapUiCmd.class);
        assertThat(voteCmd).isInstanceOf(MapUiCmd.class);
        assertThat(rtvCmd).isInstanceOf(MapUiCmd.class);
        assertThat(closeCmd).isInstanceOf(MapUiCmd.class);
    }

    @Test
    @DisplayName("Derives pure MapUiCmd side-effects from (MapUiState, MapUiEvent) transitions")
    void derivesPureSideEffectsFromTransitions() {
        var browser = new MapUiState.Browser("u", false, "", 1, 1, List.of(), 0, false);
        List<MapUiCmd> openCmds = MapUiController.evaluateCommands(browser, new MapUiEvent.OpenMapDetails("arena.msav"), 12345L);

        assertThat(openCmds).containsExactly(
                new MapUiCmd.LoadMapDetails("arena.msav"),
                new MapUiCmd.RequestPreview("arena.msav"),
                new MapUiCmd.SubscribeRtv("arena.msav", 12345L)
        );

        var details = new MapUiState.Details("u", false, "arena.msav", null, null, null, null, null, null, null);
        List<MapUiCmd> backCmds = MapUiController.evaluateCommands(details, new MapUiEvent.BackToBrowser(), 12345L);
        assertThat(backCmds).containsExactly(new MapUiCmd.UnsubscribeRtv(12345L));

        List<MapUiCmd> voteCmds = MapUiController.evaluateCommands(details, new MapUiEvent.ToggleReputation(true), 12345L);
        assertThat(voteCmds).containsExactly(new MapUiCmd.PersistReputationVote("arena.msav", true, false));

        List<MapUiCmd> triggerRtvCmds = MapUiController.evaluateCommands(details, new MapUiEvent.TriggerRtv(), 12345L);
        assertThat(triggerRtvCmds).containsExactly(new MapUiCmd.TriggerRtv("arena.msav", false));

        List<MapUiCmd> closeCmds = MapUiController.evaluateCommands(details, new MapUiEvent.Close(), 12345L);
        assertThat(closeCmds).containsExactly(
                new MapUiCmd.UnsubscribeRtv(12345L),
                new MapUiCmd.CloseSession()
        );
    }
}
