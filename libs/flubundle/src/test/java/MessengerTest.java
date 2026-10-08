import arc.files.Fi;
import arc.util.Align;
import com.ospx.flubundle.Args;
import com.ospx.flubundle.Bundle;
import com.ospx.flubundle.Text;
import com.ospx.flubundle.mindustry.Messenger;
import com.ospx.flubundle.mindustry.Popup;
import mindustry.gen.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class MessengerTest {

    private Bundle bundle;
    private Messenger messenger;

    @BeforeEach
    void setUp() {
        bundle = new Bundle(Locale.ENGLISH);
        add("greeting = Hello, { $name }!\n", Locale.ENGLISH);
        add("greeting = Привет, { $name }!\n", Locale.of("ru"));
        messenger = Messenger.of(bundle);
    }

    private void add(String ftl, Locale locale) {
        Fi temp = Fi.tempFile("messenger_test.ftl");
        temp.writeString(ftl);
        bundle.addSource(temp, locale);
    }

    private static Player player(String name, String clientLocale) {
        Player player = Player.create();
        player.name = name;
        player.locale = clientLocale;
        return player;
    }

    /** Runs a delivery with a transport that records what each player received. */
    private static Map<String, String> collect(Consumer<BiConsumer<Player, String>> delivery) {
        var received = new LinkedHashMap<String, String>();
        delivery.accept((player, text) -> received.put(player.name, text));
        return received;
    }

    @Test
    void deliversInEachPlayersLocale() {
        var players = List.of(player("a", "en"), player("b", "ru_RU"), player("c", "de"));
        var received = collect(transport ->
                messenger.to(players).deliver(Text.of("greeting", Args.of("name", "Billy")), transport));

        assertEquals(Map.of(
                "a", "Hello, Billy!",
                "b", "Привет, Billy!",
                "c", "Hello, Billy!"), received);
    }

    @Test
    void usesTheBundlesLocaleResolver() {
        Player player = player("a", "en");
        Map<Player, Locale> selected = new HashMap<>();
        selected.put(player, Locale.of("ru"));
        bundle.setLocaleResolver(selected::get);

        var received = collect(transport ->
                messenger.to(player).deliver(Text.of("greeting", Args.of("name", "Billy")), transport));

        assertEquals("Привет, Billy!", received.get("a"));
    }

    @Test
    void rendersOncePerLocale() {
        var renders = new AtomicInteger();
        bundle.setMissingKeyPolicy((key, args, locale) -> {
            renders.incrementAndGet();
            return key + "@" + locale;
        });
        var players = new ArrayList<Player>();
        for (int i = 0; i < 6; i++) {
            players.add(player("p" + i, i % 2 == 0 ? "en" : "ru"));
        }

        var received = collect(transport -> messenger.to(players).deliver(Text.of("absent"), transport));

        assertEquals(2, renders.get());
        assertEquals("absent@ru", received.get("p1"));
        assertEquals("absent@en", received.get("p2"));
    }

    @Test
    void filterNarrowsTheAudience() {
        var players = List.of(player("a", "en"), player("b", "ru"), player("c", "en"));
        var received = collect(transport -> messenger.to(players)
                .filter(player -> !player.name.equals("b"))
                .deliver(Text.of("greeting", Args.of("name", "X")), transport));

        assertEquals(List.of("a", "c"), new ArrayList<>(received.keySet()));
    }

    @Test
    void readsTheIterableAtDeliveryTime() {
        var players = new ArrayList<Player>();
        var audience = messenger.to(players);
        players.add(player("late", "ru"));

        var received = collect(transport -> audience.deliver(Text.of("greeting", Args.of("name", "Y")), transport));

        assertEquals(Map.of("late", "Привет, Y!"), received);
    }

    @Test
    void rejectsNullTargets() {
        assertThrows(NullPointerException.class, () -> messenger.to((Player) null));
        assertThrows(NullPointerException.class, () -> messenger.to((Iterable<Player>) null));
        assertThrows(NullPointerException.class, () -> messenger.team(null));
    }

    @Test
    void popupIsAnImmutableBuilder() {
        Popup base = Popup.at(Align.top);
        Popup styled = base.duration(5f).margin(80, 1, 2, 3);

        assertEquals(new Popup(Popup.DEFAULT_DURATION, Align.top, 0, 0, 0, 0), base);
        assertEquals(new Popup(5f, Align.top, 80, 1, 2, 3), styled);
        assertEquals(Align.center, Popup.center().align());
    }
}
