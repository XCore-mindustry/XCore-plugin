package org.xcore.cloud.mindustry.parser;

import mindustry.ctype.ContentType;
import mindustry.game.Team;
import mindustry.net.Administration;
import mindustry.type.Item;
import mindustry.type.Liquid;
import mindustry.type.StatusEffect;
import mindustry.type.UnitType;
import mindustry.world.Block;
import org.incendo.cloud.parser.ParserDescriptor;

/**
 * Factories for the Mindustry parsers the manager registers by default. Use them directly with
 * the builder API, e.g. {@code .required("unit", MindustryParsers.unitType())}.
 */
public final class MindustryParsers {

    private MindustryParsers() {}

    public static <C> ParserDescriptor<C, Team> team() {
        return ParserDescriptor.of(new TeamParser<>(false), Team.class);
    }

    public static <C> ParserDescriptor<C, Team> anyTeam() {
        return ParserDescriptor.of(new TeamParser<>(true), Team.class);
    }

    /**
     * A player the server has a record of, online or not: UUID, name or {@code #id} of an online
     * player, and from the console an IP.
     */
    public static <C> ParserDescriptor<C, Administration.PlayerInfo> playerInfo() {
        return ParserDescriptor.of(new PlayerInfoParser<>(), Administration.PlayerInfo.class);
    }

    public static <C> ParserDescriptor<C, UnitType> unitType() {
        return ParserDescriptor.of(new ContentParser<>(ContentType.unit, UnitType.class), UnitType.class);
    }

    public static <C> ParserDescriptor<C, Block> block() {
        return ParserDescriptor.of(new ContentParser<>(ContentType.block, Block.class), Block.class);
    }

    public static <C> ParserDescriptor<C, Item> item() {
        return ParserDescriptor.of(new ContentParser<>(ContentType.item, Item.class), Item.class);
    }

    public static <C> ParserDescriptor<C, Liquid> liquid() {
        return ParserDescriptor.of(new ContentParser<>(ContentType.liquid, Liquid.class), Liquid.class);
    }

    public static <C> ParserDescriptor<C, StatusEffect> statusEffect() {
        return ParserDescriptor.of(new ContentParser<>(ContentType.status, StatusEffect.class), StatusEffect.class);
    }
}
