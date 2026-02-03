package org.xcore.cloud.mindustry;

import arc.util.CommandHandler;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.incendo.cloud.CloudCapability;
import org.incendo.cloud.CommandManager;
import org.incendo.cloud.SenderMapper;
import org.incendo.cloud.caption.CaptionProvider;
import org.incendo.cloud.execution.ExecutionCoordinator;
import org.incendo.cloud.internal.CommandRegistrationHandler;

import java.util.Objects;
import java.util.function.BiPredicate;
import java.util.function.Supplier;

public class MindustryCommandManager<C> extends CommandManager<C> {

    private final SenderMapper<MindustrySender, C> senderMapper;
    private ConflictStrategy conflictStrategy = ConflictStrategy.SKIP;
    private BiPredicate<C, String> permissionChecker = (sender, perm) -> true;

    private Supplier<String> prefixProvider = () -> "cloud";

    public MindustryCommandManager(
            CommandHandler handler,
            ExecutionCoordinator<C> coordinator,
            SenderMapper<MindustrySender, C> senderMapper
    ) {
        super(coordinator, CommandRegistrationHandler.nullCommandRegistrationHandler());
        this.senderMapper = senderMapper;

        registerCapability(CloudCapability.StandardCapabilities.ROOT_COMMAND_DELETION);

        ArcCommandRegistrationHandler<C> regHandler = new ArcCommandRegistrationHandler<>(this, handler);
        this.commandRegistrationHandler(regHandler);

        registerDefaultExceptionHandlers();
    }

    public static MindustryCommandManager<MindustrySender> create(CommandHandler handler) {
        return new MindustryCommandManager<>(
                handler,
                ExecutionCoordinator.simpleCoordinator(),
                SenderMapper.identity()
        );
    }

    @Override
    public boolean hasPermission(@NonNull C sender, @NonNull String permission) {
        if (permission.isEmpty()) return true;
        return permissionChecker.test(sender, permission);
    }

    public SenderMapper<MindustrySender, C> senderMapper() {
        return this.senderMapper;
    }

    public void setConflictStrategy(ConflictStrategy conflictStrategy) {
        this.conflictStrategy = Objects.requireNonNull(conflictStrategy);
    }

    public ConflictStrategy getConflictStrategy() {
        return conflictStrategy;
    }

    public void setPermissionChecker(BiPredicate<C, String> permissionChecker) {
        this.permissionChecker = Objects.requireNonNull(permissionChecker);
    }

    public BiPredicate<C, String> getPermissionChecker() {
        return permissionChecker;
    }

    public void setCommandPrefix(String prefix) {
        this.prefixProvider = () -> Objects.requireNonNull(prefix);
    }

    public void setCommandPrefixProvider(Supplier<String> prefixProvider) {
        this.prefixProvider = Objects.requireNonNull(prefixProvider);
    }

    public String getCommandPrefix() {
        return prefixProvider.get();
    }

    public void addCaptionProvider(CaptionProvider<C> provider) {
        this.captionRegistry().registerProvider(provider);
    }

    private void registerDefaultExceptionHandlers() {
        registerDefaultExceptionHandlers(
                triplet -> {
                    var ctx = triplet.first();
                    String message = ctx.formatCaption(triplet.second(), triplet.third());

                    MindustrySender original = senderMapper.reverse(ctx.sender());
                    original.sendMessage("[scarlet]Error: " + message);
                },
                pair -> arc.util.Log.err("Command Error: " + pair.first(), pair.second())
        );
    }
}
