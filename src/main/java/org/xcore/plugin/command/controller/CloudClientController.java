package org.xcore.plugin.command.controller;

import org.xcore.plugin.cloud.XCoreSender;
import org.xcore.plugin.session.Session;
import org.xcore.plugin.session.SessionService;

public interface CloudClientController {

    default Session resolveSession(XCoreSender sender) {
        return resolveSession(sender, null);
    }

    default Session resolveSession(XCoreSender sender, SessionService sessionService) {
        if (sender == null) return null;
        Session s = sender.session();
        if (s != null) return s;
        return (sender.player() != null && sessionService != null)
                ? sessionService.get(sender.player().uuid())
                : null;
    }
}