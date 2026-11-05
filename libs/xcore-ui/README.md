# XCore-UI (`xcore-ui`)

> A type-safe, reactive, server-driven UI framework for Mindustry v160 servers running on Java 25.

[![License: MPL 2.0](https://img.shields.io/badge/License-MPL_2.0-brightgreen.svg)](LICENSE)
[![Release](https://img.shields.io/badge/release-0.3.0-blue.svg)](https://github.com/XCore-mindustry/xcore/releases/tag/xcore-ui/v0.3.0)
[![Java](https://img.shields.io/badge/Java-25-orange.svg)]()
[![Mindustry](https://img.shields.io/badge/Mindustry-v160-green.svg)]()

---

## Highlights

- **Partial updates**: a reducer names the slots it changed and the session sends only those, with
  `Call.menuBuilderUpdate`; the rest of the dialog keeps its scroll offsets and text field focus. The
  session remembers which slots the client has, so a patch that cannot land (the slot is new, or the
  view dropped it) is sent as the whole dialog instead of being lost.
- **Elm / MVI controllers**: state transitions are `(Model, Event) -> UpdateResult<Model>`; I/O goes
  out as effects, and a keyed effect supersedes the earlier one so a late answer never lands on a
  dialog that has moved on.
- **Record lenses and forms**: `Lens<S, V>` binds a field to a record component
  (`Lens.of(State::nickname, State::withNickname)`); `FormSchema` decodes, validates and applies a
  submit all or nothing.
- **Java DSL with lazy localization**: `Ui.table(t -> ...)` builds an immutable `VNode` tree; `Text`
  resolves at compile time against a pluggable `LocalizerResolver` (Arc `I18NBundle`, FluBundle or
  your own).
- **Layouts per screen width**: `Variants.byWidth` lays a dialog out once per class of screens and lets
  the client build the one for its own; `Responsive` places single pieces under a client condition.
- **Server-streamed textures**: `TextureRegistry` names PNGs by their SHA-256 (`net-xcore_<hash>`),
  sends each one once per connection, and sends it again when a send could not reach the player.
- **Packet guard**: `UiPackets` measures a compiled dialog and refuses one over the client's packet
  buffer before anything is sent.
- **Headless tests**: the sealed `VNode` tree compiles and runs under plain JUnit 5, no graphics.

## Changes after 0.3.0 (unreleased)

- A patch of a slot the client does not have, or one the new view no longer has, sends the whole
  dialog: before, the client ignored it and kept showing the old content. A slot nested in another
  slot of the same patch is sent once, inside the outer one, whatever their keys; and a patch whose
  bodies do not all fit a packet sends none of them.
- `FormSchema.apply` leaves the model unchanged when any field is rejected, as documented; before,
  the valid fields of a rejected submit were applied.
- `TextureRegistry` sends a texture again when the last send failed: `TextureSender.trySend` may
  answer `false` (the player is not online yet), and a sender that throws leaves the texture
  undelivered. The registry keeps a copy of the bytes it is given; `contains(region)` is new.
- `LocalizerResolver.composite` / `orElse` asks the next resolver when one gives the key itself
  back, which is how a resolver made with `from(BiFunction)` says it lacks a key.
- `VImage` rejects an unknown `scaling` where the image is made, rather than when the dialog is
  compiled; `VSpace` checks its id like the other nodes.

---

## Installation

### Gradle (Kotlin DSL)
```kotlin
repositories {
    mavenCentral()
    maven("https://maven.x-core.org/releases")
}

dependencies {
    compileOnly("org.xcore:xcore-ui:0.3.0")
    // Or implementation(...) if you shade it into your plugin jar
}
```

### Gradle (Groovy DSL)
```groovy
repositories {
    mavenCentral()
    maven { url 'https://maven.x-core.org/releases' }
}

dependencies {
    compileOnly 'org.xcore:xcore-ui:0.3.0'
}
```

### Version Catalog (`libs.versions.toml`)
```toml
[versions]
xcore-ui = "0.3.0"

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

    private static final ActionRouter<CounterEvent> ACTIONS = ActionRouter.<CounterEvent>create()
            .on("action:inc", CounterEvent.Increment::new);

    @Override
    public CounterEvent parseEvent(MenuResult result) {
        return ACTIONS.parse(result);
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

    public static final SlotKey<Object> SLOT_TABS = SlotKey.of("slot_tabs");
    public static final SlotKey<Object> SLOT_TAB = SlotKey.of("slot_tab");

    @Override
    public UpdateResult<SettingsModel> update(SettingsModel model, SettingsEvent event, ControllerContext ctx) {
        return switch (event) {
            // The tab bar and the tab are sent again; the footer stays as it is.
            case SelectTab(var tab) -> UpdateResult.patch(SettingsModel.ACTIVE_TAB.set(model, tab), SLOT_TABS, SLOT_TAB);
            case Save() -> UpdateResult.close(model);
        };
    }

    @Override
    public VNode render(SettingsModel model) {
        return Ui.table(t -> {
            t.layout(l -> l.width(500f).height(400f).pad(10f));

            // The buttons show which tab is open, so they are in a slot of their own.
            t.slot(SLOT_TABS, tabs -> {
                tabs.button(Text.t("tab.profile"), "tab:profile", b -> b.checked("profile".equals(model.activeTab())));
                tabs.button(Text.t("tab.chat"), "tab:chat", b -> b.checked("chat".equals(model.activeTab())));
            });
            t.row();

            t.slot(SLOT_TAB, tab -> tab.label(Text.t("tab." + model.activeTab() + ".body"), l -> l.grow()));
            t.row();

            t.button(Text.t("save"), "action:save", b -> b.style("green"));
        });
    }

    private static final ActionRouter<SettingsEvent> ACTIONS = ActionRouter.<SettingsEvent>create()
            .on("action:save", Save::new)
            .prefix("tab:", SelectTab::new);

    @Override
    public SettingsEvent parseEvent(MenuResult result) {
        return ACTIONS.parse(result);
    }
}
```

The view outside the patched slots must stay the same: a patch replaces what is inside a slot and
nothing else. Change anything around them with `UpdateResult.rerender(model)`.

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

Licensed under the [Mozilla Public License 2.0](LICENSE). Code contributed before the license change was received under the [MIT License](LICENSE.MIT).
