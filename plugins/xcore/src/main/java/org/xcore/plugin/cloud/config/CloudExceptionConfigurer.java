package org.xcore.plugin.cloud.config;

import arc.util.Log;
import com.ospx.flubundle.Bundle;
import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;
import mindustry.gen.Player;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.exception.ArgumentParseException;
import org.incendo.cloud.exception.InvalidSyntaxException;
import org.incendo.cloud.exception.NoPermissionException;
import org.incendo.cloud.exception.parsing.ParserException;
import org.xcore.cloud.mindustry.MindustryCommandManager;
import org.xcore.plugin.cloud.XCoreSender;
import org.xcore.plugin.cloud.exception.XCoreCommandException;
import org.xcore.plugin.session.SessionService;

import java.util.Map;

import static com.ospx.flubundle.Bundle.args;

/**
 * Turns command failures into localized messages, for players and the console alike.
 *
 * <p>Exception messages are never shown to the sender: they can carry internal details (thread
 * names, SQL, stack-derived text). Unexpected failures are logged with their stack trace instead.
 */
@Singleton
public class CloudExceptionConfigurer {

    private final Bundle bundle;
    private final Provider<SessionService> sessionService;

    @Inject
    public CloudExceptionConfigurer(Bundle bundle,
                                    Provider<SessionService> sessionService) {
        this.bundle = bundle;
        this.sessionService = sessionService;
    }

    public void configure(MindustryCommandManager<XCoreSender> manager) {
        manager.exceptionController().registerHandler(XCoreCommandException.class, ctx -> {
            XCoreCommandException ex = ctx.exception();
            if (ex.isSilent()) return;
            sendXCoreException(ctx.context().sender(), ex);
        });

        manager.exceptionController().registerHandler(InvalidSyntaxException.class, ctx ->
                send(ctx.context().sender(), "error-invalid-syntax", args("syntax", ctx.exception().correctSyntax())));

        manager.exceptionController().registerHandler(NoPermissionException.class, ctx ->
                send(ctx.context().sender(), "error-access-denied", args()));

        manager.exceptionController().registerHandler(ArgumentParseException.class, ctx -> {
            Throwable exception = ctx.exception();
            XCoreSender sender = ctx.context().sender();

            XCoreCommandException xcoreEx = findCause(exception, XCoreCommandException.class);
            if (xcoreEx != null) {
                if (xcoreEx.isSilent()) return;
                sendXCoreException(sender, xcoreEx);
                return;
            }

            ParserException parserEx = findCause(exception, ParserException.class);
            if (parserEx != null) {
                sendParserException(ctx.context(), parserEx);
                return;
            }

            // A parser rejected the input with a plain exception. Its message was written for
            // developers, so it goes to the log and the sender gets the generic text.
            Throwable cause = exception.getCause() != null ? exception.getCause() : exception;
            Log.warn("[cloud] Argument rejected for @: @: @", sender.isPlayer() ? sender.player().plainName() : "console",
                    cause.getClass().getSimpleName(), cause.getMessage());
            send(sender, "error-argument-parse-generic", args());
        });

        manager.exceptionController().registerHandler(Exception.class, ctx -> {
            Throwable exception = ctx.exception();
            XCoreSender sender = ctx.context().sender();

            XCoreCommandException xcoreEx = findCause(exception, XCoreCommandException.class);
            if (xcoreEx != null) {
                if (xcoreEx.isSilent()) return;
                sendXCoreException(sender, xcoreEx);
                return;
            }

            InvalidSyntaxException syntax = findCause(exception, InvalidSyntaxException.class);
            if (syntax != null) {
                send(sender, "error-invalid-syntax", args("syntax", syntax.correctSyntax()));
                return;
            }

            Log.err("Command failed for " + (sender.isPlayer() ? "player " + sender.player().plainName() : "console"),
                    exception);
            send(sender, "error-internal", args());
        });
    }

    /**
     * A parser failure with a Cloud caption, rendered by the manager's caption formatter
     * ({@link BundleCaptionFormatter}): the bundle message when XCore translates the key, otherwise the
     * caption's own text (the English default of Cloud or cloud-mindustry), never the raw key.
     */
    private void sendParserException(CommandContext<XCoreSender> context, ParserException parserEx) {
        String message = context.formatCaption(parserEx.errorCaption(), parserEx.captionVariables());
        boolean translated = bundle.has(CloudCaptionConfigurer.bundleKey(parserEx.errorCaption()));
        // Bundle messages carry their own colors; the English defaults have none.
        context.sender().sendMessage(translated ? message : "[scarlet]" + message);
    }

    private <T extends Throwable> T findCause(Throwable throwable, Class<T> type) {
        Throwable current = throwable;
        while (current != null) {
            if (type.isInstance(current)) {
                return type.cast(current);
            }
            Throwable cause = current.getCause();
            if (cause == current) {
                break;
            }
            current = cause;
        }
        return null;
    }

    private void sendXCoreException(XCoreSender sender, XCoreCommandException ex) {
        send(sender, ex.getKey(), ex.getArgs());
    }

    /** Sends a bundle message in the sender's language; the console gets the default locale. */
    private void send(XCoreSender sender, String key, Map<String, Object> args) {
        if (sender.isPlayer()) {
            sendToPlayer(sender.player(), key, args);
            return;
        }
        sender.sendMessage(bundle.format(sender.locale(), key, args));
    }

    private void sendToPlayer(Player player, String key, Map<String, Object> args) {
        var session = sessionService.get().get(player);
        if (session != null) {
            session.locale().send(key, args);
            return;
        }
        player.sendMessage(bundle.format(bundle.locale(player), key, args));
    }
}
