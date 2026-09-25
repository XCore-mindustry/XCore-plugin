package org.xcore.cloud.mindustry.selector;

import arc.struct.Seq;
import mindustry.gen.Entityc;
import mindustry.gen.Player;
import mindustry.gen.Unit;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.xcore.cloud.mindustry.MindustrySender;

import java.util.Optional;

/**
 * Base interface for target selectors.
 *
 * @param <T> the type of entity targeted
 */
public interface TargetSelector<T extends Entityc> {

    @NonNull String rawInput();

    @NonNull SelectorKind kind();

    @NonNull TargetSelectorSpec spec();

    boolean isSingle();

    interface SingleEntitySelector<T extends Entityc> extends TargetSelector<T> {

        @NonNull T resolve(@NonNull MindustrySender sender);

        @NonNull Optional<T> find(@NonNull MindustrySender sender);

        @Override
        default boolean isSingle() {
            return true;
        }
    }

    interface MultipleEntitySelector<T extends Entityc> extends TargetSelector<T> {

        @NonNull Seq<T> resolve(@NonNull MindustrySender sender);

        @Override
        default boolean isSingle() {
            return false;
        }
    }

    interface SinglePlayerSelector extends SingleEntitySelector<Player> {
        @Override
        @NonNull Player resolve(@NonNull MindustrySender sender);
    }

    interface MultiplePlayerSelector extends MultipleEntitySelector<Player> {
        @Override
        @NonNull Seq<Player> resolve(@NonNull MindustrySender sender);
    }

    interface SingleUnitSelector extends SingleEntitySelector<Unit> {
        @Override
        @NonNull Unit resolve(@NonNull MindustrySender sender);
    }

    interface MultipleUnitSelector extends MultipleEntitySelector<Unit> {
        @Override
        @NonNull Seq<Unit> resolve(@NonNull MindustrySender sender);
    }
}
