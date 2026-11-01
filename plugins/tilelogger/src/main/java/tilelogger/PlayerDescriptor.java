package tilelogger;

public class PlayerDescriptor {
    public String name;
    public String uuid;
    public int id;

    public PlayerDescriptor(String name, String uuid, int id) {
        this.name = name;
        this.uuid = uuid;
        this.id = id;
    }

    public String toString() {
        return name + " [gray]#[white]" + id;
    }
}
