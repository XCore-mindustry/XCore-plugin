package org.xcore.plugin.security.ingress.checks;

import com.ospx.flubundle.Bundle;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.xcore.plugin.config.GlobalConfig;
import org.xcore.plugin.security.ingress.AccessResult;
import org.xcore.plugin.security.ingress.ipreputation.IpReputationService;

import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("IpReputationCheck")
class IpReputationCheckTest {

    private IpReputationService ipReputationService;
    private IpReputationCheck check;

    @BeforeEach
    void setUp() {
        ipReputationService = mock(IpReputationService.class);
        var globalConfig = new GlobalConfig();
        globalConfig.discordUrl = "https://discord.example";

        Bundle bundle = new Bundle(Locale.ENGLISH) {
            @Override
            public Locale resolveLocale(String code) {
                return Locale.ENGLISH;
            }

            @Override
            public Locale resolveLocale(Locale locale) {
                return locale == null ? Locale.ENGLISH : locale;
            }

            @Override
            public String format(Locale locale, String id, Map<String, Object> args) {
                if ("ip-reputation-denied".equals(id)) {
                    return "ip-reputation-denied: " + args.get("discordUrl");
                }
                return id;
            }
        };

        check = new IpReputationCheck(ipReputationService, bundle, globalConfig);
    }

    @Test
    @DisplayName("shouldAllow when IP is not blocked")
    void shouldAllow_whenNotBlocked() {
        var con = new IngressChecksTestSupport.DummyConnection("1.1.1.1");
        var packet = IngressChecksTestSupport.newPacket();
        when(ipReputationService.isBlocked("1.1.1.1")).thenReturn(false);

        var result = check.check(con, packet);

        assertThat(result).isSameAs(AccessResult.Allowed.INSTANCE);
    }

    @Test
    @DisplayName("shouldDeny with localized reason when IP is blocked")
    void shouldDeny_whenBlocked() {
        var con = new IngressChecksTestSupport.DummyConnection("1.1.1.1");
        var packet = IngressChecksTestSupport.newPacket();
        when(ipReputationService.isBlocked("1.1.1.1")).thenReturn(true);

        var result = check.check(con, packet);

        assertThat(result).isInstanceOfSatisfying(AccessResult.Denied.class, denied -> {
            assertThat(denied.reason()).contains("ip-reputation-denied");
            assertThat(denied.reason()).contains("https://discord.example");
            assertThat(denied.silent()).isFalse();
        });
    }

    @Test
    @DisplayName("shouldAllow when connection address is blank")
    void shouldAllow_whenBlankAddress() {
        var con = new IngressChecksTestSupport.DummyConnection("");
        var packet = IngressChecksTestSupport.newPacket();

        var result = check.check(con, packet);

        assertThat(result).isSameAs(AccessResult.Allowed.INSTANCE);
    }

    @Test
    @DisplayName("shouldAllow when connection address is null")
    void shouldAllow_whenNullAddress() {
        var con = new IngressChecksTestSupport.DummyConnection(null);
        var packet = IngressChecksTestSupport.newPacket();

        var result = check.check(con, packet);

        assertThat(result).isSameAs(AccessResult.Allowed.INSTANCE);
    }

    @Test
    @DisplayName("shouldAllow and fail open when service throws")
    void shouldAllow_whenServiceThrows() {
        var con = new IngressChecksTestSupport.DummyConnection("1.1.1.1");
        var packet = IngressChecksTestSupport.newPacket();
        when(ipReputationService.isBlocked("1.1.1.1")).thenThrow(new RuntimeException("provider timeout"));

        var result = check.check(con, packet);

        assertThat(result).isSameAs(AccessResult.Allowed.INSTANCE);
    }

    @Test
    @DisplayName("should trim connection address before evaluation")
    void shouldTrimAddressBeforeEvaluation() {
        var con = new IngressChecksTestSupport.DummyConnection("  1.1.1.1  ");
        var packet = IngressChecksTestSupport.newPacket();
        when(ipReputationService.isBlocked("1.1.1.1")).thenReturn(false);

        var result = check.check(con, packet);

        assertThat(result).isSameAs(AccessResult.Allowed.INSTANCE);
    }
}
