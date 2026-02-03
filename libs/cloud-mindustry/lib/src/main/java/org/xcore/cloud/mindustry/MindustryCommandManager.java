package org.xcore.cloud.mindustry;

import arc.util.CommandHandler;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.incendo.cloud.CommandManager;
import org.incendo.cloud.SenderMapper;
import org.incendo.cloud.execution.ExecutionCoordinator;

public class MindustryCommandManager<C> extends CommandManager<C> {

    private final SenderMapper<MindustrySender, C> senderMapper;

    public MindustryCommandManager(
            CommandHandler handler,
            ExecutionCoordinator<C> coordinator,
            SenderMapper<MindustrySender, C> senderMapper
    ) {
        super(coordinator, null);
        this.senderMapper = senderMapper;

        this.commandRegistrationHandler(new ArcCommandInjector<>(handler, this));
    }

    @Override
    public boolean hasPermission(@NonNull C sender, String permission) {
        if (permission.isEmpty()) return true;

        MindustrySender original = senderMapper.reverse(sender);
        if (original.isPlayer() && original.player() != null) {
            return original.player().admin;
        }
        return true;
    }

    public SenderMapper<MindustrySender, C> senderMapper() {
        return this.senderMapper;
    }

    public static MindustryCommandManager<MindustrySender> create(CommandHandler handler) {
        return new MindustryCommandManager<>(
                handler,
                ExecutionCoordinator.simpleCoordinator(),
                SenderMapper.identity()
        );
    }
}