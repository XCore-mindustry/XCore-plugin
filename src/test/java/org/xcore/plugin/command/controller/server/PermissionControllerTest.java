package org.xcore.plugin.command.controller.server;

import arc.util.CommandHandler;
import arc.util.Log;
import com.ospx.flubundle.Bundle;
import org.incendo.cloud.annotations.AnnotationParser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.xcore.cloud.mindustry.MindustryCommandManager;
import org.xcore.plugin.cloud.XCoreSender;
import org.xcore.plugin.cloud.config.CloudCaptionConfigurer;
import org.xcore.plugin.cloud.config.CloudManagerFactory;
import org.xcore.plugin.cloud.config.CloudParserConfigurer;
import org.xcore.plugin.cloud.config.CommandPermissions;
import org.xcore.plugin.concurrent.Async;
import org.xcore.plugin.database.PagedDataResult;
import org.xcore.plugin.database.repository.PlayerDataRepository;
import org.xcore.plugin.metrics.MetricsService;
import org.xcore.plugin.model.PlayerData;
import org.xcore.plugin.permission.RemoteConsoleScope;
import org.xcore.plugin.permission.RolesWorld;
import org.xcore.plugin.permission.StaffCredentials;
import org.xcore.plugin.permission.grant.Grant;
import org.xcore.plugin.permission.grant.PermissionMigration;
import org.xcore.plugin.service.TimeService;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** The {@code perm} commands typed into the console, through the real command parser. */
class PermissionControllerTest {

    private final RolesWorld world = new RolesWorld();
    private final PlayerDataRepository players = mock(PlayerDataRepository.class);
    private final StaffCredentials credentials = mock(StaffCredentials.class);
    private final RemoteConsoleScope remote = new RemoteConsoleScope();
    private final List<String> errors = new ArrayList<>();
    private MindustryCommandManager<XCoreSender> manager;
    private Log.LogHandler previousLogger;

    @BeforeEach
    void setUp() {
        previousLogger = Log.logger;
        Log.logger = (level, text) -> {
            if (level == Log.LogLevel.err) {
                errors.add(text);
            }
        };

        PlayerData target = RolesWorld.data("uuid-1");
        when(world.sessionService.resolvePlayerData("uuid-1")).thenReturn(target);

        Async async = mock(Async.class);
        doAnswer(call -> {
            call.<Runnable>getArgument(0).run();
            return null;
        }).when(async).run(any());

        CloudManagerFactory factory = new CloudManagerFactory(mock(Bundle.class), () -> world.sessionService,
                mock(MetricsService.class, RETURNS_DEEP_STUBS), world.permissions, remote, mock(CloudCaptionConfigurer.class));
        manager = factory.createManager(new CommandHandler(""));
        new CloudParserConfigurer(mock(TimeService.class), mock(RETURNS_DEEP_STUBS)).configure(manager);
        AnnotationParser<XCoreSender> parser = new AnnotationParser<>(manager, XCoreSender.class);
        manager.registerMindustryAnnotations(parser);
        parser.parse(new PermissionController(world.roles, world.grants, world.sessions,
                new PermissionMigration(players, world.grants, world.roles), credentials, players,
                world.sessionService, new TimeService(), async));
    }

    private void console(String line) {
        manager.commandHandler().handleMessage(line);
    }

    private List<Grant> grants() {
        return world.store.find("uuid-1").grants();
    }

    @AfterEach
    void tearDown() {
        Log.logger = previousLogger;
    }

    @Test
    @DisplayName("The commands pass the startup check of permission nodes")
    void declared() {
        CommandPermissions.verifyDeclared(manager.commands());
    }

    @Test
    @DisplayName("role add with a server, a time span and a quoted reason")
    void roleAdd() {
        console("perm user uuid-1 role add moderator --server event --for 7d --reason \"trial week\"");

        assertThat(errors).isEmpty();
        Grant grant = grants().get(0);
        assertThat(grant.role()).isEqualTo("moderator");
        assertThat(grant.server()).isEqualTo("event");
        assertThat(grant.reason()).isEqualTo("trial week");
        assertThat(grant.expiresAt()).isEqualTo(world.clock.instant().plus(Duration.ofDays(7)));
        assertThat(grant.by()).isEqualTo("console@main");
    }

    @Test
    @DisplayName("A change without a reason is refused")
    void reasonRequired() {
        console("perm user uuid-1 role add moderator");

        assertThat(grants()).isEmpty();
        assertThat(errors).anySatisfy(error -> assertThat(error).contains("reason is required"));
    }

    @Test
    @DisplayName("deny, undeny and role remove")
    void denyAndRemove() {
        console("perm user uuid-1 role add admin --reason \"owner\"");
        console("perm user uuid-1 deny xcore.moderation.ban --for 1d --reason \"cooldown\"");
        assertThat(grants()).hasSize(2);
        String denial = grants().get(1).id();

        console("perm user uuid-1 undeny " + denial + " --reason \"served\"");
        console("perm user uuid-1 role remove admin --reason \"left\"");

        assertThat(errors).isEmpty();
        assertThat(grants()).isEmpty();
    }

    @Test
    @DisplayName("A command relayed from another server cannot change grants or reset a password, but can read")
    void remoteConsole() {
        remote.run("hub", () -> {
            console("perm user uuid-1 role add admin --reason \"from afar\"");
            console("perm user uuid-1 reset-password");
            console("perm prune");
            console("perm migrate --apply");
            console("perm user uuid-1 info");
            console("perm roles");
        });

        assertThat(grants()).isEmpty();
        verify(credentials, never()).resetPassword(any(), any());
        verify(players, never()).findDiscordRoleAdmins();
        verify(world.sessionService).resolvePlayerData("uuid-1");
    }

    @Test
    @DisplayName("reset-password goes through as the local console")
    void resetPassword() {
        console("perm user uuid-1 reset-password");

        verify(credentials).resetPassword(any(PlayerData.class), any());
    }

    @Test
    @DisplayName("A nickname shared by several players is refused instead of the first one being taken")
    void ambiguousNickname() {
        when(players.search(anyString(), anyInt(), anyInt()))
                .thenReturn(new PagedDataResult<>(2, 1, List.of(RolesWorld.data("uuid-8"), RolesWorld.data("uuid-9"))));

        console("perm user Steve role add admin --reason \"which one\"");

        assertThat(world.store.find("uuid-8").grants()).isEmpty();
        assertThat(world.store.find("uuid-9").grants()).isEmpty();
        assertThat(errors).anySatisfy(error -> assertThat(error).contains("nickname of 2 players"));
    }

    @Test
    @DisplayName("A unique nickname names its player; an unknown one is an error")
    void nickname() {
        when(players.search(anyString(), anyInt(), anyInt()))
                .thenReturn(new PagedDataResult<>(1, 1, List.of(RolesWorld.data("uuid-8"))))
                .thenReturn(null);

        console("perm user Steve role add moderator --reason \"found\"");
        console("perm user Nobody role add moderator --reason \"missing\"");

        assertThat(world.store.find("uuid-8").grants()).hasSize(1);
        assertThat(errors).anySatisfy(error -> assertThat(error).contains("not found"));
    }

    @Test
    @DisplayName("migrate reports by default and writes only with --apply")
    void migrate() {
        PlayerData admin = RolesWorld.data("uuid-1");
        admin.discordId = "900000000000000001";
        when(players.findDiscordRoleAdmins()).thenReturn(List.of(admin));

        console("perm migrate");
        assertThat(grants()).isEmpty();

        console("perm migrate --apply");
        assertThat(grants()).extracting(Grant::role).containsExactly("admin");
        assertThat(errors).isEmpty();
    }
}
