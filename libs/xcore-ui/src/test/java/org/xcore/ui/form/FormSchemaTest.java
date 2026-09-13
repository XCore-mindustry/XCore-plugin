package org.xcore.ui.form;

import org.junit.jupiter.api.Test;
import org.xcore.ui.Lens;
import mindustry.ui.builder.MenuResult;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FormSchemaTest {

    record PlayerSettings(String nickname, float volume, boolean chatEnabled, int maxPings) {
        static final Lens<PlayerSettings, String> NICKNAME =
                Lens.of(PlayerSettings::nickname, (s, v) -> new PlayerSettings(v, s.volume(), s.chatEnabled(), s.maxPings()));
        static final Lens<PlayerSettings, Float> VOLUME =
                Lens.of(PlayerSettings::volume, (s, v) -> new PlayerSettings(s.nickname(), v, s.chatEnabled(), s.maxPings()));
        static final Lens<PlayerSettings, Boolean> CHAT =
                Lens.of(PlayerSettings::chatEnabled, (s, v) -> new PlayerSettings(s.nickname(), s.volume(), v, s.maxPings()));
        static final Lens<PlayerSettings, Integer> PINGS =
                Lens.of(PlayerSettings::maxPings, (s, v) -> new PlayerSettings(s.nickname(), s.volume(), s.chatEnabled(), v));
    }

    private final FormSchema<PlayerSettings> schema = new FormSchema<PlayerSettings>()
            .bind("f_nick", PlayerSettings.NICKNAME, ValueCodec.string(),
                    List.of(Validator.notBlank("err.nick.blank"), Validator.lengthBetween(3, 16, "err.nick.len")))
            .bind("f_vol", PlayerSettings.VOLUME, ValueCodec.flt(),
                    List.of(Validator.range(0f, 100f, "err.vol.range")))
            .bind("f_chat", PlayerSettings.CHAT, ValueCodec.bool(), List.of())
            .bind("f_pings", PlayerSettings.PINGS, ValueCodec.integer(),
                    List.of(Validator.intRange(1, 10, "err.pings.range")));

    @Test
    void appliesValidResultToModel() {
        PlayerSettings initial = new PlayerSettings("OldNick", 50f, false, 3);
        MenuResult result = new MenuResult();
        result.values.put("f_nick", "NewNick");
        result.values.put("f_vol", 75.5f);
        result.values.put("f_chat", true);
        result.values.put("f_pings", 8);

        FormSchema.FormResult<PlayerSettings> res = schema.apply(initial, result);

        assertThat(res.isValid()).isTrue();
        assertThat(res.errors()).isEmpty();
        assertThat(res.model()).isEqualTo(new PlayerSettings("NewNick", 75.5f, true, 8));
    }

    @Test
    void rejectsInvalidFieldAndPreservesOldModel() {
        PlayerSettings initial = new PlayerSettings("OldNick", 50f, true, 5);
        MenuResult result = new MenuResult();
        result.values.put("f_nick", "X"); // too short: min 3
        result.values.put("f_vol", 150f); // out of range: max 100

        FormSchema.FormResult<PlayerSettings> res = schema.apply(initial, result);

        assertThat(res.isValid()).isFalse();
        assertThat(res.hasError("f_nick")).isTrue();
        assertThat(res.error("f_nick")).isEqualTo("err.nick.len");
        assertThat(res.hasError("f_vol")).isTrue();
        assertThat(res.error("f_vol")).isEqualTo("err.vol.range");
        // Model must NOT have mutated partially: atomic rejection
        assertThat(res.model().nickname()).isEqualTo("OldNick");
        assertThat(res.model().volume()).isEqualTo(50f);
    }

    @Test
    void codecsFallBackGracefullyOnMalformedData() {
        assertThat(ValueCodec.integer().decode("not-a-number")).isEqualTo(0);
        assertThat(ValueCodec.integer().decode(42)).isEqualTo(42);
        assertThat(ValueCodec.flt().decode("3.14")).isEqualTo(3.14f);
        assertThat(ValueCodec.bool().decode("true")).isTrue();
        assertThat(ValueCodec.bool().decode("gibberish")).isFalse();
        assertThat(ValueCodec.string().decode(null)).isEmpty();
    }
}
