package tilelogger;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class HistoryWireTest {
    @Test
    void versionAndWideTimeAreExplicitOnTheWire() {
        var state = new TileState();
        state.time = 70000;
        state.sequence = 4096;
        state.block = 2048;
        var page = new HistoryPage();
        page.requestId = 1;
        page.sessionId = "session";
        page.query = "tile";
        page.entries = new TileStatePacket[] {new TileStatePacket(state, "actor", "Configured", false)};
        String wire = HistoryWire.write(page);
        assertThat(wire).contains("\"version\":2", "\"time\":70000").doesNotContain("\"class\"");
        var decoded = HistoryWire.read(HistoryPage.class, wire);
        assertThat(decoded.version).isEqualTo(2);
        assertThat(decoded.entries[0].time).isEqualTo(70000);
    }
    @Test
    void untrustedRequestsCannotSelectAnArbitraryJavaType() {
        assertThatThrownBy(
                () -> HistoryWire.read(HistoryRequest.class, "{\"class\":\"java.lang.ProcessBuilder\"}"))
                .isInstanceOf(RuntimeException.class);
    }
}
