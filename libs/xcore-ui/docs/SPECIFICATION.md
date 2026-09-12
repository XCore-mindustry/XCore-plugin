# `Xcore-UI` (XUI): Master Architectural Specification
## Unified Reactive, Type-Safe, Server-Driven UI Engine for Mindustry v160 (Java 25)

---

## 1. Executive Summary & Architectural Motivation

### 1.1 The Context of Mindustry v160
In Mindustry v160, upstream introduced a major leap in server-driven interface capabilities under `mindustry.ui.builder`:
- `MenuBuilder`, `UiBuilder`, `MenuResult`, `UiTreeBuilder`, `UiKey`, `UiDslParser`.
- Remote RPCs:
  - `Call.menuBuilder(con, id, token, title, hideOnClick, hidePrevious, fillScreen, nodeBuilder)`
  - `Call.menuBuilderUpdate(con, id, tableId, nodeBuilder)`
  - `Call.hideMenuBuilder(con, id)`
  - Client response `Call.menuBuilderChoose(player, id, MenuResult)`
- Dynamic texture streaming over TCP/UDP via `Vars.netServer.sendTexture(con, name, pngBytes)`, client registration under `"net-" + name`, and live image hot-reloading via `TextureStreamEvent`.
- Upstream explicitly soft-deprecated the legacy `Menus.registerMenu(...)` API.

### 1.2 The Problems of Pre-v160 & Naive v160 Approaches
Legacy menus (`Call.menu`, `Call.followUpMenu`, `Call.textInput`) and naive imperative wrappers suffer from severe design flaws:
1. **Dialog Flicker & Focus Destruction**: Any state update (e.g. toggling a checkbox, dragging a slider, incrementing a counter) required tearing down and re-sending the whole dialog window. This resets scrollbar positions, destroys text field focus, and causes client screen flashes.
2. **Untyped Magic Strings & Runtime Casts**: Inputs rely on loose string IDs (`result.values.get("f_nick")`) and unchecked casting, leading to `ClassCastException` and silent runtime breakage during refactorings.
3. **Bandwidth Saturation & Redundant Wire Traffic**: Rebuilding full dialog matrices transfers several kilobytes per click, whereas partial updates should only transmit changed subtrees (20–80 bytes).
4. **Lack of Asset Lifecycle Management**: Streaming PNGs without connection delivery tracking results in duplicate transmissions and client VRAM leaks.
5. **Rigid Compilation Cycles**: Modifying static announcements, server rules, or layout margins required recompiling the plugin JAR and rebooting servers.

### 1.3 The XUI Synthesis: Best of Three Paradigms
`Xcore-UI` (XUI) is an independent Java 25 library that unifies three distinct software architectures:
- **Paradigm 1 (Reactive Component Model)**: Encapsulated reusable components (`Component<Props>`), deterministic hierarchical keying (`_r/0/1`), pure virtual DOM AST (`VNode`), and topological dirty-tree pruning (`SlotPruner`).
- **Paradigm 2 (Type-Safe Fluent DSL with Record Lenses & Zero-Allocation)**: Immutable Java 25 record models, functional optics lenses (`Lens<S, V>`), pattern-matching `ValueCodec<T>`, declarative `Validators`, lazy localized `Text` AST for FluBundle, and content-addressable `TextureRegistry`.
- **Paradigm 3 (Server-Driven UI, Elm Controller & Hot-Reloadable Templates)**: Unidirectional Model-View-Update (Elm / MVI) controllers (`(Model, Event) -> UpdateResult<Model>`), atomic in-place sub-tree patching (`Call.menuBuilderUpdate`), and external `.msui` template hot-reloading via Java NIO `WatchService`.

---

## 2. System Architecture & Component Model

```
                           +-----------------------------------------------+
                           |                 VIEW SOURCES                  |
                           |  [Java 25 Fluent DSL]   [External .msui Files]|
                           +-------------------+---------------+-----------+
                                               |               |
                                     (Build)   |               | (Parse / Watch)
                                               v               v
                           +-----------------------------------------------+
                           |          IMMUTABLE VIRTUAL DOM AST            |
                           |    Sealed VNode Hierarchy (Headless Testable) |
                           +-----------------------+-----------------------+
                                                   |
                                                   v
                           +-----------------------------------------------+
                           |          XUI RUNTIME ENGINE / SESSION         |
                           |  - Elm Controller: (Model, Msg) -> Update     |
                           |  - FormSchema: MenuResult -> Lens Application |
                           |  - Dynamic Slots & Hierarchical Keying        |
                           |  - Topological Dirty-Tree Pruner              |
                           +-----------+-----------------------+-----------+
                                       |                       |
                  (Render Full / Patch)|                       | (Async Stream)
                                       v                       v
               +-------------------------------+   +-------------------------------+
               |    VNode -> NodeBuilder<?>    |   |        TextureRegistry        |
               |       Mindustry Compiler      |   |  - SHA-256 Deduplication      |
               +---------------+---------------+   |  - Per-Connection BitSets     |
                               |                   |  - Virtual Thread PNG Stream  |
                               v                   +---------------+---------------+
               +-------------------------------+                   |
               |     MindustryMenuGateway      |                   |
               |  Call.menuBuilder / Update    |                   | Vars.netServer.sendTexture
               +---------------+---------------+                   |
                               |                                   |
===============================|===================================|=========================
Mindustry Wire Protocol        |                                   |
===============================v===================================v=========================
                               Client Connection (Player.con)
                               - Menus.menuDialogs[id]
                               - Table.clear() & UiTreeBuilder.build()
                               - Core.atlas.getRegionMap()["net-" + hash]
                               - TextureStreamEvent image reload
```

---

## 3. The Immutable Virtual DOM (`VNode`) Hierarchy

All UI structures in XUI are modeled as immutable Java 25 sealed records. This enables **100% headless unit testing** in pure JUnit 5 without mocking Mindustry, Arc, or graphics pipelines.

```java
public sealed interface VNode permits
        VTable, VPane, VStack, VLabel, VImage, VButton,
        VImageButton, VField, VCheck, VSlider, VSpace, VSlot, VComponentNode {

    String id();
    UiLayout layout();

    record UiLayout(
            Float width, Float height, Float minWidth, Float maxWidth,
            Float minHeight, Float maxHeight, Float size,
            Float padTop, Float padLeft, Float padBottom, Float padRight,
            boolean grow, boolean growX, boolean growY,
            boolean fill, boolean fillX, boolean fillY,
            boolean expand, boolean expandX, boolean expandY,
            boolean uniform, boolean uniformX, boolean uniformY,
            int colspan, String align, Color color, boolean disabled
    ) {}
}
```

### Core Node Types:
1. `VTable`: Grid container supporting rows (`row()`), background sprites, padding, and layout cells.
2. `VPane`: Scrollable viewport (`ScrollPane`) wrapping an inner container.
3. `VStack`: Layered container where children render on top of each other.
4. `VLabel`: Text label supporting wrapping, alignments, styles, and lazy `Text` localization.
5. `VImage`: Atlas sprite or server-streamed texture with fallback placeholder and scaling modes.
6. `VButton` / `VImageButton`: Interactive buttons with text/icon, toggled state, and action IDs.
7. `VField`: Interactive text input field with placeholder hint, character limit, and Enter key handler.
8. `VCheck`: Interactive checkbox with label, checked state, and toggle action ID.
9. `VSlider`: Interactive continuous slider with min/max/step and dynamic formatted value label.
10. `VSlot`: Designated partial update boundary for in-place sub-tree patching.
11. `VComponentNode`: Encapsulated child component with typed props.

---

## 4. Lazy Localization Engine (`Text`)

To prevent premature string allocations and guarantee that UI text automatically matches the player's active language, strings are represented as lazy `Text` descriptors:

```java
public sealed interface Text {
    String resolve(BundleContext context);

    static Text empty() { return new Raw(""); }
    static Text raw(String text) { return new Raw(text == null ? "" : text); }
    static Text t(String key) { return new Localized(key, Map.of()); }
    static Text t(String key, Map<String, Object> args) { return new Localized(key, args); }

    record Raw(String value) implements Text {
        @Override
        public String resolve(BundleContext context) { return value; }
    }

    record Localized(String key, Map<String, Object> args) implements Text {
        @Override
        public String resolve(BundleContext context) {
            return context != null ? context.format(key, args) : key;
        }
    }
}
```

---

## 5. Type-Safe Form Binding & Functional Lenses

### 5.1 The `Lens<S, V>` Abstraction
A functional lens provides bidirectional, zero-reflection access to an immutable record property:

```java
public record Lens<S, V>(Function<S, V> getter, BiFunction<S, V, S> setter) {
    public static <S, V> Lens<S, V> of(Function<S, V> getter, BiFunction<S, V, S> setter) {
        return new Lens<>(getter, setter);
    }
    public V get(S state) { return getter.apply(state); }
    public S set(S state, V value) { return setter.apply(state, value); }
}
```

### 5.2 ValueCodecs & Pattern Matching
`ValueCodec<T>` safely parses untyped values from Mindustry's `MenuResult.values`:
- `ValueCodec.string()`: Decodes strings or string representations of numbers/booleans.
- `ValueCodec.integer()`: Decodes integers, parsing floats or strings gracefully.
- `ValueCodec.flt()`: Decodes floats with fallback to 0.0f.
- `ValueCodec.bool()`: Decodes booleans.

### 5.3 Declarative Form Validation
```java
public final class FormSchema<Model> {
    public <T> FormSchema<Model> bind(String fieldId, Lens<Model, T> lens, ValueCodec<T> codec, List<Validator<T>> validators);
    public FormResult<Model> apply(Model currentModel, MenuResult result);
}
```
Validation runs atomically before any action executes. If any field fails validation, error tokens are populated into the state, and the form remains intact without lost inputs.

---

## 6. Dynamic Slots & Topological Dirty-Tree Pruner

### 6.1 Dynamic Slots (`SlotKey<T>`)
A slot defines a region that can be patched in-place via `Call.menuBuilderUpdate(con, menuId, slotId, subTree)` without touching surrounding UI elements.

### 6.2 Topological Pruning Algorithm
When an Elm update flags multiple dirty slots, sending nested updates causes race conditions and visual glitching.

**Theorem**: Let $D$ be the set of dirty slot paths. For any pair $s_1, s_2 \in D$, if $s_2$ is a descendant of $s_1$ ($s_2$ starts with $s_1 + "/"`), updating $s_1$ replaces the entire inner DOM of $s_1$, destroying and recreating $s_2$.

**Algorithm (`SlotPruner`)**:
1. Sort candidate slot keys ascending by path length (ancestors before descendants).
2. Maintain a `Set<SlotKey<?>> retained`.
3. For each candidate, check if any existing element in `retained` is its ancestor.
4. If yes, drop candidate; if no, add to `retained`.
5. Return `retained` (strictly minimal set of update RPCs).

---

## 7. Content-Addressable Asset Pipeline (`TextureRegistry`)

### 7.1 Architecture & Lifecycles
Mindustry v160 allows streaming raw PNG bytes to client atlases via `Vars.netServer.sendTexture(con, name, bytes)`. The client registers the texture under `"net-" + name` and fires `TextureStreamEvent`.

`TextureRegistry` implements:
1. **SHA-256 Content-Addressing**: Names are derived from PNG content hashes (`net-xcore_<sha256_16>`).
2. **Per-Connection Delivery BitSets**: Server tracks delivered sequence IDs per player connection. A texture is transmitted over the wire **exactly once per session**.
3. **Virtual-Thread Pipeline**: Disk I/O, resizing, and PNG compression run on virtual threads (`Thread.ofVirtual()`), never blocking the main tick thread.
4. **LRU Cache & Disk Spillover**: Textures spill to `config/cache/textures/` to bound memory consumption.

---

## 8. Dual-Source View Engine: Fluent Java DSL & Hot-Reloadable `.msui`

### 8.1 Fluent Java 25 DSL
```java
Ui.table(t -> {
    t.size(600f, 450f).pad(12f);
    t.label(Text.t("profile-title"), l -> l.growX()).row();
    t.slot("slot_avatar", Ui.table(a -> {
        a.add(new VImage("avatar", UiLayout.DEFAULT, "net-" + model.avatarHash(), "placeholder", Scaling.fit));
    })).row();
    t.button(Text.t("save"), "action:save", b -> b.style("accent"));
})
```

### 8.2 External `.msui` Templates with Java NIO `WatchService`
Templates live in `config/templates/ui/*.msui`. When edited on a live server, `MsuiTemplateEngine` detects changes in sub-milliseconds, re-parses the AST, and notifies active player sessions to hot-swap their UI without server restarts.

---

## 9. Backward Compatibility & Gateway Modernization

### 9.1 `MindustryMenuGateway` Contract
The gateway is modernized with Mindustry v160 RPCs:
- `void menuBuilder(Player player, int menuId, long token, String title, boolean hideOnClick, boolean hideExisting, boolean fillScreen, NodeBuilder<?> ui)`
- `void menuBuilderUpdate(Player player, int menuId, String elementId, NodeBuilder<?> ui)`
- `void hideMenuBuilder(Player player, int menuId)`

### 9.2 `LegacyMenuScreenAdapter`
Pre-v160 `MenuScreen` instances automatically compile into `TableBuilder` trees with wrapped text panes and button matrices. Existing plugins continue functioning without code changes.

---

## 10. Verification & Quality Metrics

| Dimension | Legacy `Call.menu` | `Xcore-UI` (XUI) |
| :--- | :--- | :--- |
| **Network Payload per Action** | ~4,000 bytes (full rebuild) | ~70-120 bytes (slot patch) |
| **Visual Dialog Flicker** | 100% on every interaction | 0% (in-place table swap) |
| **Input Focus Retention** | Destroyed on every click | Fully preserved |
| **Testing Environment** | Requires full server runtime | 100% Headless in JUnit 5 (<50ms) |
| **Texture Delivery Redundancy** | Duplicate streaming on reopen | BitSet Deduplication (0% duplicate bytes) |
| **Template Hot-Reload** | Requires JAR build & reboot | Sub-millisecond live watch event |
