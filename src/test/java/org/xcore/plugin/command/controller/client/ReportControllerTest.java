package org.xcore.plugin.command.controller.client;

import arc.util.CommandHandler;
import com.ospx.flubundle.Bundle;
import mindustry.gen.Player;
import org.incendo.cloud.SenderMapper;
import org.incendo.cloud.annotations.AnnotationParser;
import org.incendo.cloud.execution.ExecutionCoordinator;
import org.junit.jupiter.api.Test;
import org.xcore.cloud.mindustry.MindustryCommandManager;
import org.xcore.cloud.mindustry.MindustrySender;
import org.xcore.plugin.cloud.CloudService;
import org.xcore.plugin.cloud.XCoreSender;
import org.xcore.plugin.command.CloudCommandRegistrar;
import org.xcore.plugin.config.TomlXcoreConfig;
import org.xcore.plugin.session.Session;
import org.xcore.plugin.session.SessionService;
import org.xcore.plugin.ui.menu.ReportMenu;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class ReportControllerTest {
    @Test void cloudParsesOptionalSignedPidAndMultiwordNameWithoutPermission() {
        var player = mock(Player.class);
        var session = mock(Session.class);
        var sessions = mock(SessionService.class);
        when(sessions.get(player)).thenReturn(session);
        var bundle = mock(Bundle.class);
        var menu = mock(ReportMenu.class);
        var controller = new ReportController(menu);
        var mapper = SenderMapper.<MindustrySender, XCoreSender>create(
                sender -> new XCoreSender(sender, bundle, () -> sessions), XCoreSender::getHandle);
        var manager = new MindustryCommandManager<>(new CommandHandler(""), ExecutionCoordinator.simpleCoordinator(), mapper);
        manager.setPermissionChecker((sender, permission) -> false);
        var parser = new AnnotationParser<>(manager, XCoreSender.class);
        manager.registerMindustryAnnotations(parser);
        parser.parse(controller);
        var sender = mapper.map(new MindustrySender.PlayerSender(player));
        for (String input : List.of("report", "report #0", "report #-12", "report -12", "report +12", "report Very Long Player Name")) {
            // The command coordinator is synchronous in this parser test; no game I/O is involved.
            manager.commandExecutor().executeCommand(sender, input).join();
        }
        verify(menu).open(session, null);
        verify(menu).open(session, "#0");
        verify(menu).open(session, "#-12");
        verify(menu).open(session, "-12");
        verify(menu).open(session, "+12");
        verify(menu).open(session, "Very Long Player Name");
        assertThat(manager.commands()).hasSize(1);
    }

    @Test void existingRegistrarIncludesNewControllerOnEveryServer() {
        var cloud = mock(CloudService.class);
        var controller = new ReportController(mock(ReportMenu.class));
        var config = new TomlXcoreConfig(); config.server.name = "survival";
        new CloudCommandRegistrar(cloud, config, List.of(controller), List.of()).init();
        verify(cloud).registerClient(controller);
        verify(cloud).verifyPermissions();
        assertThat(ReportController.class.isAnnotationPresent(jakarta.inject.Singleton.class)).isTrue();
    }
}
