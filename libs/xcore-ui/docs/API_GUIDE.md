# `Xcore-UI` (XUI): Developer & Migration Guide

This guide explains how to construct reactive, type-safe, and zero-flicker server interfaces using `xcore-ui` on Mindustry v160 (Java 25).

---

## 1. Gradle Installation

### Repositories
Add the XCore Maven repositories to your `build.gradle.kts` (or `build.gradle`):

```kotlin
repositories {
    mavenCentral()
    maven("https://maven.x-core.org/snapshots")
    maven("https://maven.x-core.org/releases")
}
```

### Dependency
Add `xcore-ui` dependency:

```kotlin
dependencies {
    // If your plugin is loaded on a server with xcore-ui already in classpath
    compileOnly("org.xcore:xcore-ui:0.2.0")

    // Or if you are shading it directly into your plugin jar:
    // implementation("org.xcore:xcore-ui:0.2.0")
}
```

---

## 2. Core Concepts

1. **State as an Immutable Record**:
   Define your UI state as a Java 25 `record` with wither methods.
2. **Optics Lenses for Bidirectional Binding**:
   Use `Lens.of(State::getter, State::withSetter)` to bind form inputs without string identifiers.
3. **Elm State Reducer (`UiController`)**:
   Implement `update(model, event, ctx) -> UpdateResult<Model>`. Use `UpdateResult.patch(newModel, SLOT_KEY)` to re-render only the affected sub-table without dialog flickering.

---

## 3. Complete Example: Creating an Interactive Settings Screen

### Step 1: Define Model and Lenses
```java
public record SettingsModel(
    String nickname,
    boolean showLeaderboard,
    float volume,
    String activeTab
) {
    public static final Lens<SettingsModel, String> NICKNAME =
        Lens.of(SettingsModel::nickname, (s, v) -> new SettingsModel(v, s.showLeaderboard, s.volume, s.activeTab));

    public static final Lens<SettingsModel, Boolean> LEADERBOARD =
        Lens.of(SettingsModel::showLeaderboard, (s, v) -> new SettingsModel(s.nickname, v, s.volume, s.activeTab));

    public static final Lens<SettingsModel, Float> VOLUME =
        Lens.of(SettingsModel::volume, (s, v) -> new SettingsModel(s.nickname, s.showLeaderboard, v, s.activeTab));

    public static final Lens<SettingsModel, String> ACTIVE_TAB =
        Lens.of(SettingsModel::activeTab, (s, v) -> new SettingsModel(s.nickname, s.showLeaderboard, s.volume, v));
}
```

### Step 2: Define Schema & Validators
```java
FormSchema<SettingsModel> schema = new FormSchema<SettingsModel>()
    .bind("f_nick", SettingsModel.NICKNAME, ValueCodec.string(),
        List.of(Validator.lengthBetween(3, 32, "error.nickname.length")))
    .bind("f_lb", SettingsModel.LEADERBOARD, ValueCodec.bool(), List.of())
    .bind("f_vol", SettingsModel.VOLUME, ValueCodec.flt(),
        List.of(Validator.range(0f, 100f, "error.volume.range")));
```

### Step 3: Implement Controller & View
```java
public class SettingsScreen implements UiController<SettingsModel, SettingsEvent> {

    public static final SlotKey<String> SLOT_TAB = SlotKey.of("slot_tab");

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
                tb.button(Text.t("tab.general"), "tab:general", b -> b.checked("general".equals(model.activeTab())));
                tb.button(Text.t("tab.audio"), "tab:audio", b -> b.checked("audio".equals(model.activeTab())));
            })).growX().row();

            // Dynamic Tab Content Slot
            t.slot("slot_tab", renderTabContent(model)).grow().row();

            // Footer
            t.button(Text.t("save"), "action:save", b -> b.style("green"));
        }).build();
    }
}
```

---

## 4. Asynchronous Texture Streaming (`TextureRegistry`)

To show dynamic PNGs (e.g. Discord avatars or map previews):
1. Register PNG bytes and stream them to the player; the name is content-addressed:
   ```java
   String region = textureRegistry.ensureDelivered(player.uuid(), pngBytes);
   ```
2. In your UI view:
   ```java
   t.image(region, l -> l.scaling(VImage.SCALING_FIT).placeholder("nomap").size(64f));
   ```
   The client displays the placeholder and swaps it for the real texture as soon as the bytes arrive.
3. Keep the delivery markers true to what the client holds. The client drops every server
   texture when it disconnects **and whenever it loads a world**:
   ```java
   Events.on(PlayerLeave.class, e -> textureRegistry.resetDelivery(e.player.uuid()));
   Events.on(WorldLoadEvent.class, e -> textureRegistry.resetAllDeliveries());
   ```
   `unregister(region)` forgets a texture you no longer show (e.g. when a cache evicts it).

---

## 5. Headless Unit Testing in JUnit 5

No Mindustry server or mocks required:
```java
@Test
void testTabSelectionPatchesSlot() {
    var screen = new SettingsScreen();
    var initial = new SettingsModel("Player", true, 80f, "general");

    var result = screen.update(initial, new SelectTab("audio"), null);

    assertThat(result.nextState().activeTab()).isEqualTo("audio");
    assertThat(result.dirtySlots()).containsExactly(SettingsScreen.SLOT_TAB);
    assertThat(result.requiresFullRerender()).isFalse();
}
```

---

## 6. Pluggable Localization Guide

`xcore-ui` decouples view declarations from concrete localization implementations. UI controllers emit lazy `Text` descriptors that are resolved only at compilation time against a `LocalizerResolver`.

### 5.1 Connecting Bundles

Choose the adapter matching your plugin's bundle strategy:

```java
// 1. Mindustry / Arc Core.bundle or custom I18NBundle:
LocalizerResolver arcResolver = LocalizerResolver.core();
LocalizerResolver modResolver = LocalizerResolver.from(myI18NBundle);

// 2. FluBundle / Fluent (per-player session):
LocalizerResolver fluResolver = LocalizerResolver.from((key, args) -> session.locale().format(key, args));

// 3. Custom positional engine (e.g. MessageFormat in TheSiege):
LocalizerResolver siegeResolver = LocalizerResolver.positional(
    (key, args) -> Bundle.format(key, Bundle.findLocale(player), args)
);

// 4. Simple key-value lookup:
LocalizerResolver dictResolver = LocalizerResolver.from(translationMap::get);

// 5. Fallback chaining (mod bundle -> Core.bundle -> key):
LocalizerResolver chained = modResolver.orElse(LocalizerResolver.core());
```

### 5.2 Global Default vs Per-Session Resolvers

- **Global Default**: Configure once during mod initialization so callers don't need to pass a resolver to `UiSession.start`:
  ```java
  LocalizerResolver.setDefault(LocalizerResolver.core());
  UiSession.start(controller, model, ctx, gateway); // uses default
  ```
- **Per-Player Session**: Pass the player-specific resolver when opening the UI:
  ```java
  LocalizerResolver playerResolver = LocalizerResolver.from(session.locale()::format);
  UiSession.start(controller, model, ctx, gateway, playerResolver);
  ```

### 5.3 Working with `Text` Descriptors

Use the descriptor that matches your translation format:

```java
// Key only:
Text.t("save")

// Named arguments (built-in null-safe args builder, no external dependencies):
Text.t("page.info", Text.args("page", 1, "total", 10))

// Shorthand for a single named argument:
Text.t("greeting", "name", player.plainName())

// Positional arguments (for Arc I18NBundle {0}, {1}):
Text.pos("items.transferred", itemName, count)

// Lazy concatenation:
Text.join(Text.raw("[accent]"), Text.t("save"))
```

### 5.4 Dynamic In-Dialog Language Switching

When a player changes their language while a dialog is open:
```java
// Update the session's resolver; this automatically triggers a full re-render:
session.updateResolver(newPlayerResolver);
```

---

## 7. Sizing, UI Scale, and the Limits of `DialogMetrics`

`DialogMetrics` exists because a dialog needs an upper bound on width and body height. It is
**cap metadata, not viewport metadata**: the server does not know the client's screen size, UI
scale, or orientation, and it cannot measure them. Every number in `DialogMetrics` is sent to the
client as a plain float and ends up as a raw value on an Arc `Cell`.

That rawness is the important constraint, and it is not obvious from the DSL:

```java
// Mindustry UiTreeBuilder - these reach Cell unchanged, with no Scl.scl applied:
case width     -> cell.width((Float)value);
case height    -> cell.height((Float)value);
case size      -> cell.size((Float)value);
case maxWidth  -> cell.maxWidth((Float)value);
case pad       -> cell.pad((Float)value);
```

Text, by contrast, *is* scaled: `Label` and `TextButton` render glyphs through `Scl.scl`. So on a
client whose UI scale is raised above 100%, **text grows while every box we declared stays the
same size**. Fixed button heights clip their own labels, fixed paddings look proportionally
smaller, and a width cap holds less content in logical units than the same cap holds at 1x.

### What this means in practice

- Treat raw-pixel caps as tuned for a **100% UI scale**. XCore's menus are validated up to
  **105%**.
- Sizes that are meant to *contain scaled text* should be left to the text. Do not write
  `size(32f)` on a button whose only content is a glyph, or `height(40f)` on a labelled button -
  remove the constraint and let the button's own scaled padding derive the box. This is correct at
  every UI scale.
- Prefer `growX()` / `growY()` with a cap on the *dialog* over caps on individual cards. A card
  that grows into its dialog adapts; a card with its own raw cap fights the text inside it.
- Decorative chrome (separator rules, accent stripes) is fine at a fixed pixel value - it is
  supposed to stay thin.

### What cannot be fixed here

There is no server-side workaround, because the scale is not sent to us. `ConnectPacket` carries
no UI dimensions, no UI scale, and no scene size. The one scale-aware measurement the client
exposes is a `condition` width, which is logical (`graphicsWidth / Scl.scl(1f)`).

Gating a width cap on such a condition does work, and was tried: it emits the dialog body into both
condition branches, so the whole subtree ships twice. On the `/help` list that doubled the payload
for no measurable rendering benefit, so it was removed. Keeping it would also mean duplicating
every card on every menu open, which is the wrong trade for a layout nicety.

The real fix belongs upstream: `UiTreeBuilder` should pass dimension values through `Scl.scl`, the
same way it already does for text. That is a small change in one method and would make every
dimension in this DSL scale-aware at once.

---

## 8. Session Lifecycle, Effects and Routing

### Closing
- The reducer closes its dialog by returning `UpdateResult.close(model)`; `ControllerContext.close()`
  is for closing from outside the reducer. Doing both closes twice.
- When the player closes the dialog, the cancelled result never reaches `parseEvent`: the session
  ends and `UiController.closed(model)` is called. Nothing is sent back.
- A session another dialog has replaced should be `detach()`ed (`UiSession.Registry.register` does
  it for you): it then sends nothing, and its pending effects stop delivering.
- `willReportClose()` tells whether the client will still report a close: Mindustry stops reporting
  it after the first press in a dialog, and a patch keeps that dialog.

### Effects
A reducer must not do I/O itself. It returns the work as an effect, run once the new model has been
applied and sent:
```java
case Refresh() -> UpdateResult.rerender(model.loading())
        .effect("list", (Dispatcher<TopEvent> d) -> async.supply(this::fetch)
                .thenMain((data, error) -> d.dispatch(new Loaded(data))));
```
The dispatcher delivers only while the session is active, and a keyed one only while no newer
effect of the same key was started, so a late answer never lands on a dialog that moved on.
`session.run(key, effect)` starts one from outside the reducer (e.g. the first load).

### Routing actions
```java
private static final ActionRouter<HelpEvent> ACTIONS = ActionRouter.<HelpEvent>create()
        .on("action:back", BackToList::new)
        .onResult("action:search", r -> new Search(r.getString("field_search", "")))
        .prefixInt("action:page:", OpenPage::new)
        .prefix("action:cmd:", SelectCommand::new);
```
An unknown action parses to `null`, which the session ignores: a stray id never closes a dialog.

### Layout per class of screens
The client does not shrink a dialog to its screen: a wrapped label asks for the width of its whole
line, and a scroll pane grants it. Give every piece a width and lay the dialog out once per class of
screens; `Variants.byWidth` nests the conditions so each client builds only its own layout:
```java
static final List<Variant<Float>> SCREENS = List.of(
        new Variant<>("wide", 800f, 700f), new Variant<>("narrow", 490f, 450f), new Variant<>("small", 0f, 330f));

public VNode render(Model model) {
    return Variants.byWidth(SCREENS, v -> window(model, v.value()));
}
// a slot per layout, and a patch that names them all:
window.slot(Variants.slot(SLOT_LIST, v), ...);
return UpdateResult.patch(next, Variants.slots(SCREENS, SLOT_LIST));
```

### Measuring words
A view that fits text by measuring it needs the words now, not a lazy `Text.t`; implement
`render(Model, RenderContext)` and use `context.t(key, args)`, which resolves with the session's
resolver.

### Packet size
A dialog travels as one packet and the client reads it into a 32 KB buffer. `UiSession` refuses to
send a dialog or a slot body over `UiPackets.LIMIT` and throws `UiPackets.TooLargeException` instead;
`UiPackets.size(compiled)` measures one in tests.

### Checking on the real client
`./gradlew exportUiFixtures` writes the menus whose real-client layout backs this guide (a root
with a width, a slot with rows, variants per screen) to `build/ui-render/`. Render them with the
renderer of [mindustry-testkit](https://github.com/XCore-mindustry/mindustry-testkit/tree/main/render):

```sh
../mindustry-testkit/render/render --profiles phone,android1080,pc build/ui-render
```

What each menu must show is in the javadoc of `RealClientFixtures`. A change to the compiler or
to `Variants` should be rendered before it is released.
