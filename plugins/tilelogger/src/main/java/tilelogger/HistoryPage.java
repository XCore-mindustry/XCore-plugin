package tilelogger;

public class HistoryPage {
    public int version = 2;
    public long requestId;
    public String sessionId, nextCursor = "", query;
    public boolean truncated;
    public TileStatePacket[] entries = new TileStatePacket[0];
}
