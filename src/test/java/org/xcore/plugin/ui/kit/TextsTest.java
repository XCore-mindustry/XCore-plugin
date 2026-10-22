package org.xcore.plugin.ui.kit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.xcore.plugin.localization.Localization;
import org.xcore.plugin.session.Session;

import static com.ospx.flubundle.Bundle.args;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TextsTest {

    @Test
    @DisplayName("a text is in the player's language, with or without arguments, whatever the fallback")
    void t_translates() {
        Localization ru = LayoutAssert.localization("ru");

        assertThat(Texts.t(ru, "none")).isEqualTo("Нет");
        assertThat(Texts.t(ru, "audit-menu-page", args("page", 3))).contains("3").doesNotContain("audit-menu-page");
        assertThat(Texts.t(ru, "none", "None")).isEqualTo("Нет");
        assertThat(Texts.t(ru, "audit-menu-page", args("page", 3), "3")).isEqualTo(Texts.t(ru, "audit-menu-page", args("page", 3)));
    }

    @Test
    @DisplayName("without a localization a text is its key, as it is for a text the bundle lacks")
    void t_givesTheKeyWithoutLocalization() {
        assertThat(Texts.t((Localization) null, "none")).isEqualTo("none");
        assertThat(Texts.t((Localization) null, "audit-menu-page", args("page", 3))).isEqualTo("audit-menu-page");
        assertThat(Texts.t(LayoutAssert.localization("en"), "no-such-text")).isEqualTo("no-such-text");
    }

    @Test
    @DisplayName("without a localization a fallback is given instead of the key")
    void t_givesTheFallbackWithoutLocalization() {
        assertThat(Texts.t((Localization) null, "hexed-ranks-NEWBIE", "NEWBIE")).isEqualTo("NEWBIE");
        assertThat(Texts.t((Localization) null, "player-menu-time-hours", args("value", 2), "2h")).isEqualTo("2h");
    }

    @Test
    @DisplayName("a session's text is in its player's language; without a session or its localization it is the key")
    void t_overASession() {
        Localization en = LayoutAssert.localization("en");
        Session session = mock(Session.class);
        when(session.locale()).thenReturn(en);

        assertThat(Texts.locale(session)).isSameAs(en);
        assertThat(Texts.t(session, "none")).isEqualTo(en.t("none"));
        assertThat(Texts.t(session, "audit-menu-page", args("page", 3))).isEqualTo(en.t("audit-menu-page", args("page", 3)));

        assertThat(Texts.locale(null)).isNull();
        assertThat(Texts.t((Session) null, "none")).isEqualTo("none");
        assertThat(Texts.t(mock(Session.class), "audit-menu-page", args("page", 3))).isEqualTo("audit-menu-page");
    }

    @Test
    @DisplayName("the resolver speaks the language of the localization it is asked for at each use")
    void resolver_followsTheLocalization() {
        java.util.concurrent.atomic.AtomicReference<Localization> current =
                new java.util.concurrent.atomic.AtomicReference<>(LayoutAssert.localization("ru"));
        var resolver = Texts.resolver(current::get);

        assertThat(resolver.format("none")).isEqualTo("Нет");
        assertThat(resolver.has("none")).isTrue();
        assertThat(resolver.has("no-such-text")).isFalse();
        assertThat(resolver.locale().getLanguage()).isEqualTo("ru");

        current.set(LayoutAssert.localization("en"));
        assertThat(resolver.format("none")).isEqualTo("None");
        assertThat(resolver.locale().getLanguage()).isEqualTo("en");

        current.set(null);
        assertThat(resolver.format("none")).isEqualTo("none");
        assertThat(resolver.has("none")).isFalse();
        assertThat(resolver.locale()).isEqualTo(java.util.Locale.ENGLISH);
    }
}
