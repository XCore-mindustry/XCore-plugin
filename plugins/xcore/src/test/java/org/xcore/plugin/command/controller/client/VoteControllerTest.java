package org.xcore.plugin.command.controller.client;

import mindustry.game.Team;
import mindustry.gen.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.xcore.plugin.cloud.XCoreSender;
import org.xcore.plugin.localization.Localization;
import org.xcore.plugin.model.PlayerData;
import org.xcore.plugin.permission.PermissionService;
import org.xcore.plugin.permission.RolesWorld;
import org.xcore.plugin.permission.role.PermissionSet;
import org.xcore.plugin.session.Session;
import org.xcore.plugin.session.SessionService;
import org.xcore.plugin.vote.VoteKick;
import org.xcore.plugin.vote.VoteKickFactory;
import org.xcore.plugin.vote.VoteService;
import org.xcore.plugin.vote.VoteSession;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class VoteControllerTest {

    private SessionService sessions;
    private VoteService voteService;
    private VoteKickFactory voteKickFactory;
    private VoteController controller;
    private Session session;
    private XCoreSender sender;

    @BeforeEach
    void setUp() {
        voteService = mock(VoteService.class);
        voteKickFactory = mock(VoteKickFactory.class);
        sessions = mock(SessionService.class);
        PermissionService permissions = RolesWorld.permissions(() -> sessions);
        controller = new VoteController(sessions, voteService, voteKickFactory, permissions);

        session = session("starter", permissions);
        session.data = new PlayerData();
        session.localization = mock(Localization.class);
        when(session.localization.format(anyString(), anyMap())).thenReturn("kicked");

        sender = mock(XCoreSender.class);
        when(sender.session()).thenReturn(session);
        when(sender.player()).thenReturn(session.player);
    }

    /** An online player whose session the registry holds. */
    private Session session(String uuid, PermissionService permissions) {
        Session online = mock(Session.class, CALLS_REAL_METHODS);
        online.permissions = permissions;
        online.permissionSet = PermissionSet.EMPTY;
        online.player = player(uuid);
        when(sessions.get(uuid)).thenReturn(online);
        return online;
    }

    private static Player player(String uuid) {
        Player player = mock(Player.class);
        when(player.uuid()).thenReturn(uuid);
        when(player.team()).thenReturn(Team.sharded);
        return player;
    }

    @Test
    @DisplayName("Starting a votekick against an admin kicks the starter instead")
    void votekick_adminIsImmune() {
        Session admin = RolesWorld.asAdmin(session("admin", session.permissions));

        controller.votekick(sender, admin.player, "griefing");

        verify(session.player).kick(anyString(), anyLong());
        verify(voteService, never()).startVote(any());
    }

    @Test
    @DisplayName("A votekick against a regular teammate starts")
    void votekick_regularPlayer() {
        Player target = session("target", session.permissions).player;
        VoteKick kick = mock(VoteKick.class);
        when(voteKickFactory.create(session.player, target, "griefing")).thenReturn(kick);

        controller.votekick(sender, target, "griefing");

        verify(voteService).startVote(kick);
        verify(session.player, never()).kick(anyString(), anyLong());
    }

    @Test
    @DisplayName("/vote c cancels the vote for an admin and is refused to a player")
    void voteCancel() {
        VoteSession current = mock(VoteSession.class);
        when(voteService.getCurrentSession()).thenReturn(current);

        controller.vote(sender, "c");

        verify(current, never()).cancelByAdmin(any());
        verify(session.localization).send("error-access-denied");

        RolesWorld.asAdmin(session);
        controller.vote(sender, "c");

        verify(current).cancelByAdmin(session.player);
    }
}
