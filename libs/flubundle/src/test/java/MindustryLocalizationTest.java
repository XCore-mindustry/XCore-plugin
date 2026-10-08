import arc.Core;
import arc.files.Fi;
import arc.util.I18NBundle;
import com.ospx.flubundle.Args;
import com.ospx.flubundle.Bundle;
import com.ospx.flubundle.LocaleResolver;
import com.ospx.flubundle.Text;
import com.ospx.flubundle.compiler.CompilationResult;
import com.ospx.flubundle.compiler.FtlCompiler;
import com.ospx.flubundle.mindustry.ContentNames;
import com.ospx.flubundle.mindustry.Markup;
import com.ospx.flubundle.mindustry.Messenger;
import mindustry.Vars;
import mindustry.core.ContentLoader;
import mindustry.game.Team;
import mindustry.gen.Player;
import mindustry.net.NetConnection;
import mindustry.net.Packets;
import mindustry.type.Item;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class MindustryLocalizationTest {

    private static final Locale RU = Locale.of("ru");

    @BeforeAll
    static void setUpContentLoading() {
        // Content constructors register themselves and read Core.bundle, as on a dedicated server
        // where the bundle is empty.
        if (Vars.content == null) {
            Vars.content = new ContentLoader();
        }
        if (Core.bundle == null) {
            Core.bundle = I18NBundle.createEmptyBundle();
        }
    }

    private static Bundle bundle(String en, String ru) {
        Bundle bundle = new Bundle(Locale.ENGLISH);
        add(bundle, en, Locale.ENGLISH);
        add(bundle, ru, RU);
        return bundle;
    }

    private static void add(Bundle bundle, String ftl, Locale locale) {
        Fi file = Fi.tempFile("mindustry_localization_test.ftl");
        file.writeString(ftl);
        bundle.addSource(file, locale);
    }

    private static Packets.ConnectPacket packet(String uuid, String locale) {
        Packets.ConnectPacket packet = new Packets.ConnectPacket();
        packet.uuid = uuid;
        packet.locale = locale;
        return packet;
    }

    // --- vanilla content names ---

    @Test
    void contentNamesFollowTheLocaleChain() {
        ContentNames names = ContentNames.vanilla();

        assertTrue(names.locales().containsAll(List.of("en", "ru", "uk_UA", "zh_CN", "pt_BR")));
        assertEquals("Router", names.get("block.router.name", Locale.ENGLISH));
        assertEquals("Маршрутизатор", names.get("block.router.name", RU));
        assertEquals("Маршрутизатор", names.get("block.router.name", Locale.of("ru", "RU")), "region falls back to language");
        assertEquals(names.get("block.router.name", Locale.of("uk", "UA")), names.get("block.router.name", Locale.of("uk")),
                "a bare language finds its regional file");
        assertEquals(names.get("item.copper.name", Locale.of("zh", "CN")), names.get("item.copper.name", Locale.of("zh")));
        assertEquals("Router", names.get("block.router.name", Locale.of("xx")), "unknown locales use English");
        assertEquals("Router", names.get("block.router.name", null));
        assertNull(names.get("block.not-a-block.name", RU));
    }

    @Test
    void teamsAndContentRenderLocalizedAsArguments() {
        Bundle bundle = bundle("""
                captured = { $team } captured { $item }
                """, """
                captured = { $team } захватили { $item }
                """);
        Item copper = new Item("copper");
        Map<String, Object> args = Args.of("team", Team.sharded, "item", copper);

        assertEquals("Sharded captured Copper", bundle.format(Locale.ENGLISH, "captured", args));
        assertEquals("Расколотые захватили Медь", bundle.format(RU, "captured", args));
        assertEquals("Медь", ContentNames.vanilla().name(copper, RU));
    }

    @Test
    void unknownContentAndTeamsKeepTheirOwnNames() {
        Item custom = new Item("flubundle-test-custom-item");
        assertEquals("flubundle-test-custom-item", ContentNames.vanilla().name(custom, RU));
        assertEquals(Team.get(42).name, ContentNames.vanilla().name(Team.get(42), RU));
    }

    // --- markup escaping ---

    @Test
    void escapeKeepsArgumentsFromChangingColors() {
        Bundle bundle = bundle("""
                said = [accent]{ $player }[]: { ESCAPE($message) }
                raw = [accent]{ $player }[]: { $message }
                """, "");
        Map<String, Object> args = Args.of("player", "[red]Bob", "message", "[red]x[] y");

        assertEquals("[accent][red]Bob[]: [[red]x[[] y", bundle.format(Locale.ENGLISH, "said", args));
        assertEquals("[accent][red]Bob[]: [red]x[] y", bundle.format(Locale.ENGLISH, "raw", args));
        assertEquals("[[[[a", Markup.escape("[[a"));
        assertEquals("plain", Markup.escape("plain"));
        assertNull(Markup.escape(null));
        assertEquals("x y", Markup.strip("[red]x[] y"));
    }

    @Test
    void compilerKnowsEscape(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("bundle_en.ftl"), "said = { ESCAPE($message) }\n");
        CompilationResult result = FtlCompiler.compile(dir);
        assertFalse(result.hasErrors(), result.formatReport());
    }

    // --- before the player joins ---

    @Test
    void connectPacketLocaleUsesTheClientLocaleByDefault() {
        Bundle bundle = bundle("hi = Hi\n", "hi = Привет\n");

        assertEquals(RU, bundle.locale(packet("u1", "ru_RU")));
        assertEquals(Locale.ENGLISH, bundle.locale(packet("u1", "de")), "unsupported locales fall back");
        assertEquals(Locale.ENGLISH, bundle.locale((Packets.ConnectPacket) null));
    }

    @Test
    void resolverCanPickTheStoredLanguageByUuid() {
        Bundle bundle = bundle("hi = Hi\n", "hi = Привет\n");
        bundle.setLocaleResolver(new LocaleResolver() {
            @Override
            public Locale resolve(Player player) {
                return null;
            }

            @Override
            public Locale resolve(Packets.ConnectPacket packet) {
                return "stored-ru".equals(packet.uuid) ? RU : null;
            }
        });

        assertEquals(RU, bundle.locale(packet("stored-ru", "en")));
        assertEquals(Locale.ENGLISH, bundle.locale(packet("other", "en")));
        assertEquals("Привет", Messenger.of(bundle).connecting(null, packet("stored-ru", "en")).format("hi"));
    }

    @Test
    void connectionAudienceKicksInTheResolvedLocale() {
        Bundle bundle = bundle("banned = Banned: { $reason }\n", "banned = Бан: { $reason }\n");
        List<String> kicks = new ArrayList<>();
        NetConnection connection = new NetConnection("127.0.0.1") {
            @Override
            public void send(Object object, boolean reliable) {
            }

            @Override
            public void close() {
            }

            @Override
            public void kick(String reason, long duration) {
                kicks.add(reason + "@" + duration);
            }

            @Override
            public void kick(String reason) {
                kicks.add(reason + "@default");
            }
        };

        var audience = Messenger.of(bundle).connecting(connection, packet("u", "ru"));
        audience.kick(Text.of("banned", Args.of("reason", "grief")), 1000L);
        audience.kick("banned", Args.of("reason", Markup.escape("[red]x")));

        assertEquals("Бан: grief@1000", kicks.get(0));
        assertEquals("Бан: [[red]x@default", kicks.get(1));
        assertEquals(RU, audience.locale());
        assertThrows(NullPointerException.class, () -> Messenger.of(bundle).connecting(null, packet("u", "ru")).kick("banned"));
        assertThrows(NullPointerException.class, () -> Messenger.of(bundle).connecting(connection, null));
    }
}
