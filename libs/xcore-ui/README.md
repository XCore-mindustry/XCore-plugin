# XCore-UI (`xcore-ui`)

> A type-safe, reactive, server-driven UI framework for Mindustry v160 servers running on Java 25.

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)
[![Release](https://img.shields.io/badge/release-0.1.0-blue.svg)](https://github.com/XCore-mindustry/xcore-ui/releases/tag/v0.1.0)
[![Java](https://img.shields.io/badge/Java-25-orange.svg)]()
[![Mindustry](https://img.shields.io/badge/Mindustry-v160-green.svg)]()

---

## Highlights

- **Zero-Flicker Partial Updates**: Emits granular `Call.menuBuilderUpdate` calls targeting dynamic slots. Preserves client scrollbar offsets and text field focus without dialog tearing.
- **Pure Elm / MVI Architecture**: State transitions are pure functions `(Model, Event) -> UpdateResult<Model>`. Business logic is strictly decoupled from presentation.
- **Type-Safe Record Lenses (`Lens<S, V>`)**: Binds UI inputs directly to immutable Java 25 record components with method references (`State::nickname`, `State::withNickname`), completely eliminating magic string typos.
- **Dual-Source Presentation Engine**:
  - **Fluent Java 25 DSL**: Compile-time checked, zero-allocation builder with lazy pluggable localization (Arc I18NBundle, FluBundle, or custom).
  - **Hot-Reloadable `.msui` Templates**: External DSL files reloaded in sub-milliseconds via Java NIO `WatchService` without server restarts.
- **Topological Subtree Pruning (`SlotPruner`)**: Automatically drops redundant descendant slot updates when an ancestor container is already dirty, preventing network packet collisions.
- **Content-Addressable Asset Pipeline (`TextureRegistry`)**: Streams PNGs to client atlases (`net-<hash>`) with SHA-256 deduplication, BitSet connection delivery tracking, and virtual-thread I/O.
- **100% Headless Testability**: The sealed Virtual DOM (`VNode`) runs under pure JUnit 5 in milliseconds without graphics contexts or mocks.
- **Seamless Backward Compatibility**: `LegacyMenuScreenAdapter` transparently compiles pre-v160 `MenuScreen` records into v160 `NodeBuilder<?>` trees.

---

## Installation

### Gradle (Kotlin DSL)
```kotlin
repositories {
    mavenCentral()
    maven("https://maven.x-core.org/snapshots")
    maven("https://maven.x-core.org/releases")
}

dependencies {
    compileOnly("org.xcore:xcore-ui:0.1.0")
    // Or implementation(...) if you shade it into your plugin jar
}
```

### Gradle (Groovy DSL)
```groovy
repositories {
    mavenCentral()
    maven { url 'https://maven.x-core.org/snapshots' }
    maven { url 'https://maven.x-core.org/releases' }
}

dependencies {
    compileOnly 'org.xcore:xcore-ui:0.1.0'
}
```

### Version Catalog (`libs.versions.toml`)
```toml
[versions]
xcore-ui = "0.1.0"

[libraries]
xcore-ui = { module = "org.xcore:xcore-ui", version.ref = "xcore-ui" }
```

---

## Getting Started in 3 Steps

### 1. Register Mindustry Menu Handler
In your plugin's `init()` or service startup:
```java
// Register a Mindustry v160 menu builder listener
int menuId = Menus.registerMenuBuilder((player, result) -> {
    UiSession<?, ?> session = activeSessions.get(player.uuid());
    if (session != null) {
        session.handle(result);
    }
});

// Reusable DeliveryGateway transmitting over Mindustry network
UiSession.DeliveryGateway gateway = new UiSession.DeliveryGateway() {
    @Override
    public void show(String playerId, long token, UiBuilder.NodeBuilder<?> ui) {
        Player p = Groups.player.find(player -> player.uuid().equals(playerId));
        if (p != null) Call.menuBuilder(p.con, menuId, token, null, false, true, false, ui);
    }

    @Override
    public void update(String playerId, long token, String elementId, UiBuilder.NodeBuilder<?> ui) {
        Player p = Groups.player.find(player -> player.uuid().equals(playerId));
        if (p != null) Call.menuBuilderUpdate(p.con, menuId, elementId, ui);
    }

    @Override
    public void hide(String playerId) {
        Player p = Groups.player.find(player -> player.uuid().equals(playerId));
        if (p != null) Call.hideMenuBuilder(p.con, menuId);
    }
};
```

### 2. Define Model & Reactive Controller
```java
public record CounterModel(int count) {}

public sealed interface CounterEvent {
    record Increment() implements CounterEvent {}
}

public class CounterController implements UiController<CounterModel, CounterEvent> {
    public static final SlotKey<Object> SLOT_COUNT = SlotKey.of("slot_count");

    @Override
    public CounterModel initialModel(Object ctx) {
        return new CounterModel(0);
    }

    @Override
    public UpdateResult<CounterModel> update(CounterModel model, CounterEvent event, ControllerContext ctx) {
        return switch (event) {
            case CounterEvent.Increment() ->
                UpdateResult.patch(new CounterModel(model.count() + 1), SLOT_COUNT);
        };
    }

    @Override
    public VNode render(CounterModel model) {
        return Ui.table(t -> {
            t.slot("slot_count", s -> s.label(Text.raw("Count: " + model.count())));
            t.row();
            t.button(Text.raw("+1"), "action:inc", b -> b.size(120f, 40f));
        });
    }

    @Override
    public CounterEvent parseEvent(MenuResult result) {
        if ("action:inc".equals(result.result)) return new CounterEvent.Increment();
        return null;
    }
}
```

### 3. Open the UI Session
```java
ControllerContext ctx = new ControllerContext() {
    @Override public String playerId() { return player.uuid(); }
    @Override public void close() { activeSessions.remove(player.uuid()); }
};

UiSession<CounterModel, CounterEvent> session = UiSession.start(
    new CounterController(), new CounterModel(0), ctx, gateway
);
activeSessions.put(player.uuid(), session);
session.open();
```

---

## Documentation

- [Master Architectural Specification](docs/SPECIFICATION.md)
- [ADR 0001: Hybrid Reactive Server UI Engine](docs/ADR/0001-hybrid-reactive-server-ui.md)
- [Developer & Migration Guide](docs/API_GUIDE.md)

---

## Quick Example

```java
public class PlayerSettingsScreen implements UiController<SettingsModel, SettingsEvent> {

    public static final SlotKey<String> SLOT_TAB = SlotKey.of("_r/0/slot_tab", String.class);

    @Override
    public SettingsModel initialModel() {
        return new SettingsModel("Player", true, 80f, "profile");
    }

    @Override
    public UpdateResult<SettingsModel> update(SettingsModel model, SettingsEvent event, ControllerContext ctx) {
        if (event instanceof SelectTab select) {
            SettingsModel updated = SettingsModel.ACTIVE_TAB.set(model, select.tab());
            // Partial in-place patch: only slot_tab updates over the wire!
            return UpdateResult.patch(updated, SLOT_TAB);
        }
        return UpdateResult.of(model);
    }

    public VNode render(SettingsModel model) {
        return Ui.table(t -> {
            t.size(500f, 400f).pad(10f);

            // Tab Bar
            t.add(Ui.table(tb -> {
                tb.button(Text.t("tab.profile"), "tab:profile", b -> b.checked("profile".equals(model.activeTab())));
                tb.button(Text.t("tab.chat"), "tab:chat", b -> b.checked("chat".equals(model.activeTab())));
            })).growX().row();

            // Dynamic Tab Content Slot (patched in-place with zero flicker)
            t.slot("slot_tab", renderTabContent(model)).grow().row();

            // Footer
            t.button(Text.t("save"), "action:save", b -> b.style("green"));
        }).build();
    }
}
```

---

## Pluggable Localization (`LocalizerResolver` & `Text`)

`xcore-ui` is completely agnostic of any concrete localization library. User-facing text is written with lazy descriptors (`Text`) and resolved at compilation time against a pluggable `LocalizerResolver`.

### 1. Connecting Bundles in 1 Line

```java
// Arc / Mindustry Core.bundle (positional {0}, {1}):
LocalizerResolver resolver = LocalizerResolver.core();
// Or custom I18NBundle:
LocalizerResolver resolver = LocalizerResolver.from(myModBundle);

// FluBundle / Fluent (named parameters):
LocalizerResolver resolver = LocalizerResolver.from(session.locale()::format);

// Custom MessageFormat / TheSiege:
LocalizerResolver resolver = LocalizerResolver.positional((key, args) -> Bundle.format(key, locale, args));

// Simple Map / Properties:
LocalizerResolver resolver = LocalizerResolver.from(dictionaryMap::get);

// Composite fallback chain:
LocalizerResolver resolver = modResolver.orElse(LocalizerResolver.core());
```

Set a global default resolver once so sessions don't need to pass it explicitly:
```java
LocalizerResolver.setDefault(LocalizerResolver.core());
```

### 2. Using `Text` Descriptors

```java
// Key-only
t.label(Text.t("dialog.title"));

// Named parameters (using built-in zero-dependency Text.args)
t.label(Text.t("map.page.info", Text.args("page", 1, "total", 10)));

// Positional parameters (for Arc I18NBundle {0}, {1})
t.label(Text.pos("player.online.count", 42));

// Raw unlocalized text & lazy joining
t.button(Text.join(Text.raw("[accent]"), Text.t("btn.save")), "action:save");
```

---

## License

Licensed under the [MIT License](LICENSE).
