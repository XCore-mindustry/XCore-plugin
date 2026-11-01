package org.xcore.plugin.security.ingress.checks;

import arc.struct.Seq;
import mindustry.Vars;
import mindustry.mod.Mods;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.xcore.plugin.security.ingress.AccessResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("ModCompatibilityCheck")
class ModCompatibilityCheckTest {

    private IngressChecksTestSupport.VarsState varsState;
    private Mods mods;
    private ModCompatibilityCheck check;

    @BeforeEach
    void setUp() {
        varsState = IngressChecksTestSupport.captureVarsState();
        mods = mock(Mods.class);
        Vars.mods = mods;
        check = new ModCompatibilityCheck();
    }

    @AfterEach
    void tearDown() {
        varsState.restore();
    }

    @Test
    @DisplayName("shouldAllow_whenMissingAndExtraModsAreEmpty")
    void shouldAllow_whenMissingAndExtraModsAreEmpty() {
        var packet = IngressChecksTestSupport.newPacket();
        packet.mods = Seq.with();
        when(mods.getIncompatibility(any())).thenReturn(Seq.with());

        var result = check.check(new IngressChecksTestSupport.DummyConnection("1.1.1.1"), packet);

        assertThat(result).isSameAs(AccessResult.Allowed.INSTANCE);
    }

    @Test
    @DisplayName("shouldDenyWithIncompatibleModsText_whenMissingOrExtraModsExist")
    void shouldDenyWithIncompatibleModsText_whenMissingOrExtraModsExist() {
        var packet = IngressChecksTestSupport.newPacket();
        packet.mods = Seq.with("extra-mod");
        when(mods.getIncompatibility(any())).thenReturn(Seq.with("required-mod"));

        var result = check.check(new IngressChecksTestSupport.DummyConnection("1.1.1.1"), packet);

        assertThat(result).isInstanceOfSatisfying(AccessResult.Denied.class, denied -> {
            assertThat(denied.reason()).contains("Incompatible mods!");
            assertThat(denied.reason()).contains("Missing:");
            assertThat(denied.reason()).contains("required-mod");
            assertThat(denied.reason()).contains("Unnecessary mods:");
            assertThat(denied.reason()).contains("extra-mod");
        });
    }
}
