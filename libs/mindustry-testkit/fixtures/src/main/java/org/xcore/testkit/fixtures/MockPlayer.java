package org.xcore.testkit.fixtures;

import mindustry.core.NetClient;
import mindustry.game.Team;
import mindustry.gen.Building;
import mindustry.gen.Player;
import mindustry.gen.Unit;
import mindustry.type.UnitType;
import mindustry.world.Tile;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Fixture representing a simulated connected player backed by an in-memory {@link MockNetConnection}.
 */
public final class MockPlayer {
    private final Player player;
    private final MockNetConnection connection;

    private MockPlayer(Builder builder) {
        this.player = Player.create();
        this.player.name = Objects.requireNonNull(builder.name, "name cannot be null");
        this.player.team(Objects.requireNonNull(builder.team, "team cannot be null"));
        this.player.admin = builder.admin;
        this.player.locale = builder.locale;
        this.player.set(builder.x, builder.y);

        this.connection = new MockNetConnection(builder.address);
        this.connection.uuid = builder.uuid;
        this.connection.usid = builder.usid;
        this.connection.player = this.player;
        this.player.con = this.connection;

        if (builder.autoAdd) {
            this.player.add();
        }
    }

    public Player player() {
        return player;
    }

    public MockNetConnection con() {
        return connection;
    }

    public int id() {
        return player.id;
    }

    public String name() {
        return player.name;
    }

    public String uuid() {
        return connection.uuid;
    }

    public Team team() {
        return player.team();
    }

    public boolean isAdmin() {
        return player.admin;
    }

    public float x() {
        return player.x;
    }

    public float y() {
        return player.y;
    }

    public int tileX() {
        return (int) (player.x / 8f);
    }

    public int tileY() {
        return (int) (player.y / 8f);
    }

    public Tile tileOn() {
        return player.tileOn();
    }

    public Building buildOn() {
        return player.buildOn();
    }

    public Unit unit() {
        return player.unit();
    }

    public List<Object> sentPackets() {
        return connection.sentPackets();
    }

    public <T> List<T> sentPackets(Class<T> type) {
        return connection.sentPackets().stream()
                .filter(type::isInstance)
                .map(type::cast)
                .collect(Collectors.toList());
    }

    public <T> T lastPacket(Class<T> type) {
        List<T> list = sentPackets(type);
        return list.isEmpty() ? null : list.get(list.size() - 1);
    }

    public List<String> receivedMessages() {
        return connection.messages();
    }

    public String lastReceivedMessage() {
        return connection.lastMessage();
    }

    public List<String> announcements() {
        return connection.announcements();
    }

    public String lastAnnouncement() {
        return connection.lastAnnouncement();
    }

    public List<String> infoPopups() {
        return connection.infoMessages();
    }

    public String lastInfoPopup() {
        return connection.lastInfoMessage();
    }

    public List<String> warningToasts() {
        return connection.warningToasts();
    }

    public String lastWarningToast() {
        return connection.lastWarningToast();
    }

    public boolean isKicked() {
        return connection.kicked || connection.isClosed();
    }

    public String kickReason() {
        return connection.kickReason();
    }

    public void setPosition(float x, float y) {
        player.set(x, y);
    }

    public void setTile(int tileX, int tileY) {
        player.set(tileX * 8f, tileY * 8f);
    }

    public void setTeam(Team team) {
        player.team(team);
    }

    public void setAdmin(boolean admin) {
        player.admin = admin;
    }

    public void sendChat(String text) {
        NetClient.sendChatMessage(player, text);
    }

    public void runCommand(String commandLine) {
        String full = commandLine.startsWith("/") ? commandLine : "/" + commandLine;
        sendChat(full);
    }

    public void spawnUnit(UnitType type) {
        Unit u = type.spawn(player.team(), player.x, player.y);
        player.unit(u);
    }

    public void remove() {
        player.remove();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String name = "Player";
        private String uuid = UUID.randomUUID().toString();
        private String usid = UUID.randomUUID().toString();
        private String address = "127.0.0.1";
        private Team team = Team.sharded;
        private boolean admin = false;
        private String locale = "en";
        private float x = 0f;
        private float y = 0f;
        private boolean autoAdd = true;

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder uuid(String uuid) {
            this.uuid = uuid;
            return this;
        }

        public Builder usid(String usid) {
            this.usid = usid;
            return this;
        }

        public Builder address(String address) {
            this.address = address;
            return this;
        }

        public Builder team(Team team) {
            this.team = team;
            return this;
        }

        public Builder admin(boolean admin) {
            this.admin = admin;
            return this;
        }

        public Builder locale(String locale) {
            this.locale = locale;
            return this;
        }

        public Builder position(float x, float y) {
            this.x = x;
            this.y = y;
            return this;
        }

        public Builder positionTiles(int tileX, int tileY) {
            this.x = tileX * 8f;
            this.y = tileY * 8f;
            return this;
        }

        public Builder autoAdd(boolean autoAdd) {
            this.autoAdd = autoAdd;
            return this;
        }

        public MockPlayer build() {
            return new MockPlayer(this);
        }
    }
}
