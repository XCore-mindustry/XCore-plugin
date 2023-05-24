package tilelogger;

public class TileStatePacket {
    public short x;
    public short y;
    public String name;
    public String uuid;
    public boolean valid;
    public short time;
    public short block;
    public boolean destroy;
    public short rotation;
    public short config_type;
    public String config;

    public TileStatePacket(short x, short y, String name, String uuid, boolean valid, short time, short block, boolean destroy, short rotation, short config_type, String config) {
        this.x = x;
        this.y = y;
        this.name = name;
        this.uuid = uuid;
        this.valid = valid;
        this.time = time;
        this.block = block;
        this.destroy = destroy;
        this.rotation = rotation;
        this.config_type = config_type;
        this.config = config;
    }
}
