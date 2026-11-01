package tilelogger;

/** Untrusted client input. Server binds continuation cursors to its own query. */
public class HistoryRequest {
    public long requestId;
    public String type = "tile", uuid = "", cursor = "";
    public int x, y;
}
