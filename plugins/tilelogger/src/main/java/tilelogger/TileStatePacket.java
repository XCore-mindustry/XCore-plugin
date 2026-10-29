package tilelogger;

/** v2; legacy responses narrow only their display clock explicitly. */
public class TileStatePacket {
    public int x, y, block, rotation, config_type, action_kind;
    public String name, uuid, config, action;
    public boolean valid, destroy, legacy;
    public long time, sequence;
    public TileStatePacket() {}
    public TileStatePacket(TileState state, String name, String action, boolean legacyWire) {
        x = state.x;
        y = state.y;
        block = state.block;
        rotation = state.rotation;
        config_type = state.config_type;
        action_kind = state.action_kind;
        uuid = state.uuid;
        valid = state.valid;
        destroy = state.destroy;
        sequence = state.sequence;
        time = legacyWire ? (short) (state.time & 0xffff) : state.time;
        legacy = legacyWire || state.legacy;
        this.name = name;
        this.action = action;
        config = state.getConfigAsString();
    }
}
