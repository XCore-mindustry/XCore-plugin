package com.ospx.flubundle.mindustry;

import com.ospx.flubundle.Bundle;
import mindustry.game.Team;
import mindustry.gen.Groups;
import mindustry.gen.Player;

import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

/**
 * Delivers localized messages to players. Each player receives the message in the locale picked
 * by {@link Bundle#locale(Player)}, so a {@link com.ospx.flubundle.LocaleResolver} installed on
 * the bundle applies here too.
 *
 * <pre>{@code
 * Messenger messenger = Messenger.of(bundle);
 * messenger.to(player).send("hexed_spectator_eliminated");
 * messenger.all().announce("hexed_game_finished", Args.of("winner", winner));
 * messenger.team(team).toast(Iconc.warning, "core-under-attack");
 * messenger.filter(p -> p.admin).send(Text.of("report-received", Args.of("target", name)));
 * }</pre>
 */
public final class Messenger {

    private final Bundle bundle;

    private Messenger(Bundle bundle) {
        this.bundle = Objects.requireNonNull(bundle, "bundle");
    }

    public static Messenger of(Bundle bundle) {
        return new Messenger(bundle);
    }

    public Bundle bundle() {
        return bundle;
    }

    /** One player. */
    public Audience to(Player player) {
        Objects.requireNonNull(player, "player");
        return new Audience(bundle, () -> List.of(player));
    }

    /** A fixed group of players. The iterable is read when a message is delivered. */
    public Audience to(Iterable<? extends Player> players) {
        Objects.requireNonNull(players, "players");
        return new Audience(bundle, () -> players);
    }

    /** Every player online when a message is delivered. */
    public Audience all() {
        return new Audience(bundle, () -> Groups.player);
    }

    /** Online players of a team. */
    public Audience team(Team team) {
        Objects.requireNonNull(team, "team");
        return all().filter(player -> player.team() == team);
    }

    /** Online players matching a condition. */
    public Audience filter(Predicate<? super Player> condition) {
        return all().filter(condition);
    }
}
