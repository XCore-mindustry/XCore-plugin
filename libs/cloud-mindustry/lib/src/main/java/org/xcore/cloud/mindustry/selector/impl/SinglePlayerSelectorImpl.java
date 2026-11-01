package org.xcore.cloud.mindustry.selector.impl;

import arc.struct.Seq;
import mindustry.gen.Player;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.xcore.cloud.mindustry.MindustrySender;
import org.xcore.cloud.mindustry.selector.SelectorKind;
import org.xcore.cloud.mindustry.selector.TargetSelector.SinglePlayerSelector;
import org.xcore.cloud.mindustry.selector.TargetSelectorSpec;
import org.xcore.cloud.mindustry.selector.engine.SpatialSelectorEngine;
import org.xcore.cloud.mindustry.selector.exception.NoSuchTargetException;
import org.xcore.cloud.mindustry.selector.exception.TooManyTargetsException;

import java.util.Optional;

public final class SinglePlayerSelectorImpl implements SinglePlayerSelector {

    private final String rawInput;
    private final SelectorKind kind;
    private final TargetSelectorSpec spec;
    private final SpatialSelectorEngine engine;

    public SinglePlayerSelectorImpl(
            String rawInput,
            SelectorKind kind,
            TargetSelectorSpec spec,
            SpatialSelectorEngine engine
    ) {
        this.rawInput = rawInput;
        this.kind = kind;
        this.spec = spec;
        this.engine = engine;
    }

    @Override
    public @NonNull String rawInput() {
        return rawInput;
    }

    @Override
    public @NonNull SelectorKind kind() {
        return kind;
    }

    @Override
    public @NonNull TargetSelectorSpec spec() {
        return spec;
    }

    @Override
    public @NonNull Player resolve(@NonNull MindustrySender sender) {
        Seq<Player> list = engine.resolvePlayers(sender, spec);
        if (list.isEmpty()) {
            throw new NoSuchTargetException(rawInput);
        }
        if (list.size > 1) {
            throw new TooManyTargetsException(rawInput, "Expected single player for '" + rawInput + "' but found " + list.size);
        }
        return list.first();
    }

    @Override
    public @NonNull Optional<Player> find(@NonNull MindustrySender sender) {
        try {
            return Optional.of(resolve(sender));
        } catch (NoSuchTargetException e) {
            return Optional.empty();
        }
    }
}
