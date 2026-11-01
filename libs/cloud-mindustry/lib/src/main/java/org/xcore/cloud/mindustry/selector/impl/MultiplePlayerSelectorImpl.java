package org.xcore.cloud.mindustry.selector.impl;

import arc.struct.Seq;
import mindustry.gen.Player;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.xcore.cloud.mindustry.MindustrySender;
import org.xcore.cloud.mindustry.selector.SelectorKind;
import org.xcore.cloud.mindustry.selector.TargetSelector.MultiplePlayerSelector;
import org.xcore.cloud.mindustry.selector.TargetSelectorSpec;
import org.xcore.cloud.mindustry.selector.engine.SpatialSelectorEngine;

public final class MultiplePlayerSelectorImpl implements MultiplePlayerSelector {

    private final String rawInput;
    private final SelectorKind kind;
    private final TargetSelectorSpec spec;
    private final SpatialSelectorEngine engine;

    public MultiplePlayerSelectorImpl(
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
    public @NonNull Seq<Player> resolve(@NonNull MindustrySender sender) {
        return engine.resolvePlayers(sender, spec);
    }
}
