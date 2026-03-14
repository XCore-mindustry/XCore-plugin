package org.xcore.plugin.cloud.config;

import com.ospx.flubundle.Bundle;
import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;
import mindustry.gen.Player;
import org.incendo.cloud.caption.CaptionVariable;
import org.incendo.cloud.exception.ArgumentParseException;
import org.incendo.cloud.exception.InvalidSyntaxException;
import org.incendo.cloud.exception.NoPermissionException;
import org.incendo.cloud.exception.parsing.ParserException;
import org.xcore.cloud.mindustry.MindustryCommandManager;
import org.xcore.plugin.cloud.XCoreSender;
import org.xcore.plugin.cloud.exception.XCoreCommandException;
import org.xcore.plugin.session.SessionService;

import java.util.HashMap;
import java.util.Map;

import static com.ospx.flubundle.Bundle.args;

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

            XCoreSender sender = ctx.context().sender();
            sendXCoreException(sender, ex);
        });

        manager.exceptionController().registerHandler(InvalidSyntaxException.class, ctx -> {
            InvalidSyntaxException ex = ctx.exception();
            String correctSyntax = ex.correctSyntax();
            XCoreSender sender = ctx.context().sender();
            if (sender.isPlayer()) {
                sendToPlayer(sender.player(), "error-invalid-syntax", args("syntax", correctSyntax));
            } else {
                sender.sendMessage("Invalid Syntax. Usage: " + correctSyntax);
            }
        });

        manager.exceptionController().registerHandler(NoPermissionException.class, ctx -> {
            XCoreSender sender = ctx.context().sender();
            String key = "error-access-denied";

            if (sender.isPlayer()) {
                sendToPlayer(sender.player(), key, args());
            } else {
                sender.sendMessage("Error: " + key);
            }
        });

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
                String key = parserEx.errorCaption().key().replace(".", "-");

                Map<String, Object> arguments = new HashMap<>();
                for (CaptionVariable variable : parserEx.captionVariables()) {
                    arguments.put(variable.key(), variable.value());
                }

                if (sender.isPlayer()) {
                    sendToPlayer(sender.player(), key, arguments);
                } else {
                    sender.sendMessage("Parse Error (" + key + "): " + parserEx.getMessage());
                }
                return;
            }

            Throwable cause = rootCause(exception);
            String errorMsg = cause.getMessage();
            if (sender.isPlayer()) {
                sendToPlayer(sender.player(), "error-argument-parse-generic", args("error", errorMsg));
            } else {
                sender.sendMessage("Parse Error: " + errorMsg);
            }
        });

        manager.exceptionController().registerHandler(Exception.class, ctx -> {
            Throwable exception = ctx.exception();

            XCoreCommandException xcoreEx = findCause(exception, XCoreCommandException.class);
            if (xcoreEx != null) {
                if (xcoreEx.isSilent()) return;
                XCoreSender sender = ctx.context().sender();
                sendXCoreException(sender, xcoreEx);
                return;
            }

            Throwable cause = rootCause(exception);
            String messageKey = "error-internal";

            if (findCause(exception, InvalidSyntaxException.class) != null) {
                messageKey = "error-invalid-syntax";
            }

            XCoreSender sender = ctx.context().sender();
            if (sender.isPlayer()) {
                sendToPlayer(sender.player(), messageKey, args());
            } else {
                sender.sendMessage("[red]System Error: " + cause.getMessage());
                cause.printStackTrace();
            }
        });
    }

    private Throwable rootCause(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current;
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
        if (sender.isPlayer()) {
            sendToPlayer(sender.player(), ex.getKey(), ex.getArgs());
            return;
        }
        sender.sendMessage(bundle.format(sender.locale(), ex.getKey(), ex.getArgs()));
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
