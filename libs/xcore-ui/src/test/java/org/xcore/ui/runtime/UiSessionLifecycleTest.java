package org.xcore.ui.runtime;

import mindustry.ui.builder.MenuResult;
import mindustry.ui.builder.UiBuilder;
import org.junit.jupiter.api.Test;
import org.xcore.ui.LocalizerResolver;
import org.xcore.ui.Text;
import org.xcore.ui.Ui;
import org.xcore.ui.UiPackets;
import org.xcore.ui.VNode;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UiSessionLifecycleTest {

    record Model(int count, int size) {}

    sealed interface Event {
        record Load() implements Event {}
        record Loaded(int count) implements Event {}
        record Grow(int size) implements Event {}
        record Close() implements Event {}
    }

    static final class Controller implements UiController<Model, Event> {
        final List<Dispatcher<Event>> pending = new ArrayList<>();
        final List<String> log = new ArrayList<>();
        Model closedWith;

        @Override
        public UpdateResult<Model> update(Model model, Event event, ControllerContext ctx) {
            return switch (event) {
                case Event.Load() -> UpdateResult.rerender(model)
                        .effect("load", (Dispatcher<Event> d) -> {
                            log.add("effect sees " + d.isCurrent());
                            pending.add(d);
                        });
                case Event.Loaded(int count) -> UpdateResult.rerender(new Model(count, model.size()));
                case Event.Grow(int size) -> UpdateResult.rerender(new Model(model.count(), size));
                case Event.Close() -> UpdateResult.close(model);
            };
        }

        @Override
        public VNode render(Model model, RenderContext context) {
            log.add("render " + model.count());
            return Ui.table(t -> {
                t.label(Text.raw(context.t("count") + " " + model.count()));
                t.label(Text.raw("x".repeat(model.size())));
            });
        }

        @Override
        public Event parseEvent(MenuResult result) {
            log.add("parse " + result.result);
            return switch (result.result) {
                case "load" -> new Event.Load();
                case "close" -> new Event.Close();
                default -> null;
            };
        }

        @Override
        public void closed(Model model) {
            closedWith = model;
        }
    }

    static final class Gateway implements UiSession.DeliveryGateway {
        final List<String> sent = new ArrayList<>();

        @Override
        public void show(String playerId, long token, UiBuilder.NodeBuilder<?> ui) {
            sent.add("show");
        }

        @Override
        public void update(String playerId, long token, String elementId, UiBuilder.NodeBuilder<?> ui) {
            sent.add("update " + elementId);
        }

        @Override
        public void hide(String playerId) {
            sent.add("hide");
        }

        @Override
        public void closed(String playerId) {
            sent.add("closed by " + playerId);
        }
    }

    static final ControllerContext CTX = new ControllerContext() {
        @Override
        public String playerId() {
            return "p";
        }

        @Override
        public void close() {
        }
    };

    private final Controller controller = new Controller();
    private final Gateway gateway = new Gateway();
    private final UiSession<Model, Event> session = UiSession.start(controller, new Model(0, 1), CTX, gateway,
            (key, args) -> "Count");

    private MenuResult press(String action) {
        MenuResult result = new MenuResult(action);
        result.token = session.token();
        return result;
    }

    private MenuResult cancel() {
        MenuResult result = new MenuResult();
        result.token = session.token();
        return result;
    }

    @Test
    void renderGetsTheSessionsResolver() {
        session.open();

        assertThat(controller.log).contains("render 0");
        assertThat(RenderContext.of((key, args) -> "Zählen").t("count")).isEqualTo("Zählen");
    }

    @Test
    void cancelGoesToClosedAndSendsNothing() {
        session.open();
        session.handle(cancel());

        assertThat(controller.closedWith).isEqualTo(new Model(0, 1));
        assertThat(controller.log).noneMatch(line -> line.startsWith("parse"));
        assertThat(session.isActive()).isFalse();
        assertThat(gateway.sent).containsExactly("show", "closed by p");
    }

    @Test
    void closingByReducerHidesOnce() {
        session.open();
        session.handle(press("close"));
        session.close();

        assertThat(gateway.sent).containsExactly("show", "hide");
        assertThat(session.isActive()).isFalse();
    }

    @Test
    void detachedSessionSendsNothing() {
        session.open();
        session.detach();
        session.dispatch(new Event.Grow(3));
        session.open();
        session.close();

        assertThat(gateway.sent).containsExactly("show");
        assertThat(session.model().size()).isEqualTo(1);
    }

    @Test
    void effectRunsAfterTheUpdateIsSentAndDeliversBack() {
        session.open();
        session.handle(press("load"));

        assertThat(gateway.sent).containsExactly("show", "show");
        assertThat(controller.log).containsSubsequence("render 0", "render 0", "effect sees true");
        assertThat(controller.pending.getFirst().dispatch(new Event.Loaded(7))).isTrue();
        assertThat(session.model().count()).isEqualTo(7);
    }

    @Test
    void newerEffectOfTheSameKeySupersedesTheOlder() {
        session.open();
        session.dispatch(new Event.Load());
        session.dispatch(new Event.Load());

        Dispatcher<Event> older = controller.pending.get(0);
        Dispatcher<Event> newer = controller.pending.get(1);
        assertThat(older.dispatch(new Event.Loaded(1))).isFalse();
        assertThat(newer.dispatch(new Event.Loaded(2))).isTrue();
        assertThat(session.model().count()).isEqualTo(2);
    }

    @Test
    void effectStopsDeliveringOnceTheSessionEnds() {
        session.open();
        session.dispatch(new Event.Load());
        session.handle(cancel());

        assertThat(controller.pending.getFirst().isCurrent()).isFalse();
        assertThat(controller.pending.getFirst().dispatch(new Event.Loaded(5))).isFalse();
        assertThat(session.model().count()).isZero();
    }

    @Test
    void runStartsAnEffectFromOutsideTheReducer() {
        session.open();
        session.run("load", d -> d.dispatch(new Event.Loaded(4)));

        assertThat(session.model().count()).isEqualTo(4);
    }

    @Test
    void closeIsReportedUntilTheFirstPressAndAgainAfterAFreshDialog() {
        session.open();
        assertThat(session.willReportClose()).isTrue();

        session.handle(press("nothing"));
        assertThat(session.willReportClose()).isFalse();

        session.dispatch(new Event.Grow(2));
        assertThat(session.willReportClose()).isTrue();
    }

    @Test
    void dialogOverThePacketLimitIsNotSent() {
        session.open();

        assertThatThrownBy(() -> session.dispatch(new Event.Grow(UiPackets.LIMIT)))
                .isInstanceOf(UiPackets.TooLargeException.class);
        assertThat(gateway.sent).containsExactly("show");
    }

    @Test
    void registryDetachesTheSessionItReplaces() {
        UiSession.Registry registry = new UiSession.Registry();
        UiSession<Model, Event> next = UiSession.start(controller, new Model(0, 1), CTX, gateway, LocalizerResolver.IDENTITY);

        registry.register("p", session);
        registry.register("p", next);

        assertThat(session.isActive()).isFalse();
        assertThat(next.isActive()).isTrue();
        assertThat(registry.<Model, Event>get("p")).isSameAs(next);
    }

    @Test
    void routerMapsActionsAndIgnoresStrayOnes() {
        record Page(int page) {}
        MenuResult search = new MenuResult("action:search");
        search.values.put("q", "abc");
        ActionRouter<Object> router = ActionRouter.create()
                .on("action:edit:cancel", () -> "cancel")
                .prefix("action:edit:", rest -> "edit " + rest)
                .prefixInt("action:page:", Page::new)
                .onResult("action:search", r -> r.getString("q", ""));

        assertThat(router.parse(new MenuResult(" action:edit:cancel "))).isEqualTo("cancel");
        assertThat(router.parse(new MenuResult("action:edit:save"))).isEqualTo("edit save");
        assertThat(router.parse(new MenuResult("action:page:3"))).isEqualTo(new Page(3));
        assertThat(router.parse(new MenuResult("action:page:x"))).isNull();
        assertThat(router.parse(search)).isEqualTo("abc");
        assertThat(router.parse(new MenuResult("action:unknown"))).isNull();
        assertThat(router.parse(new MenuResult())).isNull();
        assertThatThrownBy(() -> router.on("action:search", () -> "again")).isInstanceOf(IllegalArgumentException.class);
    }
}
