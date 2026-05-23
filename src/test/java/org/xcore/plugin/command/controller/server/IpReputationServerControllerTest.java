package org.xcore.plugin.command.controller.server;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.xcore.plugin.cloud.XCoreSender;
import org.xcore.plugin.security.ingress.ipreputation.IpReputationAllowlist;
import org.xcore.plugin.security.ingress.ipreputation.IpReputationResult;
import org.xcore.plugin.security.ingress.ipreputation.IpReputationService;

import java.util.Set;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class IpReputationServerControllerTest {

    @Test
    @DisplayName("lookup delegates to service and handles result")
    void lookup_delegatesToService() {
        var service = mock(IpReputationService.class);
        var allowlist = mock(IpReputationAllowlist.class);
        var sender = mock(XCoreSender.class);
        var controller = new IpReputationServerController(service, allowlist);

        when(service.lookup("1.2.3.4")).thenReturn(new IpReputationResult("1.2.3.4", true, false, false));

        controller.lookup(sender, "1.2.3.4");

        verify(service).lookup("1.2.3.4");
    }

    @Test
    @DisplayName("lookup handles null result gracefully")
    void lookup_handlesNullResult() {
        var service = mock(IpReputationService.class);
        var allowlist = mock(IpReputationAllowlist.class);
        var sender = mock(XCoreSender.class);
        var controller = new IpReputationServerController(service, allowlist);

        when(service.lookup("1.2.3.4")).thenReturn(null);

        controller.lookup(sender, "1.2.3.4");

        verify(service).lookup("1.2.3.4");
    }

    @Test
    @DisplayName("lookup rejects blank ip")
    void lookup_rejectsBlankIp() {
        var service = mock(IpReputationService.class);
        var allowlist = mock(IpReputationAllowlist.class);
        var sender = mock(XCoreSender.class);
        var controller = new IpReputationServerController(service, allowlist);

        controller.lookup(sender, "  ");

        verify(service, never()).lookup(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("check delegates to isBlocked")
    void check_delegatesToIsBlocked() {
        var service = mock(IpReputationService.class);
        var allowlist = mock(IpReputationAllowlist.class);
        var sender = mock(XCoreSender.class);
        var controller = new IpReputationServerController(service, allowlist);

        when(service.isBlocked("1.2.3.4")).thenReturn(true);

        controller.check(sender, "1.2.3.4");

        verify(service).isBlocked("1.2.3.4");
    }

    @Test
    @DisplayName("check rejects blank ip")
    void check_rejectsBlankIp() {
        var service = mock(IpReputationService.class);
        var allowlist = mock(IpReputationAllowlist.class);
        var sender = mock(XCoreSender.class);
        var controller = new IpReputationServerController(service, allowlist);

        controller.check(sender, "");

        verify(service, never()).isBlocked(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("allowAdd delegates to allowlist")
    void allowAdd_delegatesToAllowlist() {
        var service = mock(IpReputationService.class);
        var allowlist = mock(IpReputationAllowlist.class);
        var sender = mock(XCoreSender.class);
        var controller = new IpReputationServerController(service, allowlist);

        when(allowlist.add("1.2.3.4")).thenReturn(true);

        controller.allowAdd(sender, "1.2.3.4");

        verify(allowlist).add("1.2.3.4");
    }

    @Test
    @DisplayName("allowAdd rejects blank ip")
    void allowAdd_rejectsBlankIp() {
        var service = mock(IpReputationService.class);
        var allowlist = mock(IpReputationAllowlist.class);
        var sender = mock(XCoreSender.class);
        var controller = new IpReputationServerController(service, allowlist);

        controller.allowAdd(sender, null);

        verify(allowlist, never()).add(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("allowRemove delegates to allowlist")
    void allowRemove_delegatesToAllowlist() {
        var service = mock(IpReputationService.class);
        var allowlist = mock(IpReputationAllowlist.class);
        var sender = mock(XCoreSender.class);
        var controller = new IpReputationServerController(service, allowlist);

        when(allowlist.remove("1.2.3.4")).thenReturn(true);

        controller.allowRemove(sender, "1.2.3.4");

        verify(allowlist).remove("1.2.3.4");
    }

    @Test
    @DisplayName("allowRemove rejects blank ip")
    void allowRemove_rejectsBlankIp() {
        var service = mock(IpReputationService.class);
        var allowlist = mock(IpReputationAllowlist.class);
        var sender = mock(XCoreSender.class);
        var controller = new IpReputationServerController(service, allowlist);

        controller.allowRemove(sender, "   ");

        verify(allowlist, never()).remove(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("allowList delegates to allowlist")
    void allowList_delegatesToAllowlist() {
        var service = mock(IpReputationService.class);
        var allowlist = mock(IpReputationAllowlist.class);
        var sender = mock(XCoreSender.class);
        var controller = new IpReputationServerController(service, allowlist);

        when(allowlist.list()).thenReturn(Set.of("1.2.3.4", "5.6.7.8"));

        controller.allowList(sender);

        verify(allowlist).list();
    }

    @Test
    @DisplayName("allowList handles empty allowlist")
    void allowList_handlesEmptyAllowlist() {
        var service = mock(IpReputationService.class);
        var allowlist = mock(IpReputationAllowlist.class);
        var sender = mock(XCoreSender.class);
        var controller = new IpReputationServerController(service, allowlist);

        when(allowlist.list()).thenReturn(Set.of());

        controller.allowList(sender);

        verify(allowlist).list();
    }
}
