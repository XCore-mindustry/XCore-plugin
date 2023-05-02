package tilelogger;

public class TileStatePacket {
    public short x;
    public short y;
    public String name;
    public String uuid;
    public short time;
    public boolean valid;
    public short block;
    public short rotation;
    public short config_type;
    public String config;

    public TileStatePacket(short x, short y, String name, String uuid, short time, boolean valid, short block, short rotation, short config_type, String config) {
        this.x = x;
        this.y = y;
        this.name = name;
        this.uuid = uuid;
        this.time = time;
        this.valid = valid;
        this.block = block;
        this.rotation = rotation;
        this.config_type = config_type;
        this.config = config;
    }
}
