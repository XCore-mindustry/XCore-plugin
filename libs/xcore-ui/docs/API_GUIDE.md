# `Xcore-UI` (XUI): Developer & Migration Guide

This guide explains how to construct reactive, type-safe, and zero-flicker server interfaces using `xcore-ui` on Mindustry v160 (Java 25).

---

## 1. Quickstart: The 3 Core Concepts

1. **State as an Immutable Record**:
   Define your UI state as a Java 25 `record` with wither methods.
2. **Optics Lenses for Bidirectional Binding**:
   Use `Lens.of(State::getter, State::withSetter)` to bind form inputs without string identifiers.
3. **Elm State Reducer (`UiController`)**:
   Implement `update(model, event, ctx) -> UpdateResult<Model>`. Use `UpdateResult.patch(newModel, SLOT_KEY)` to re-render only the affected sub-table without dialog flickering.

---

## 2. Complete Example: Creating an Interactive Settings Screen

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

    public static final SlotKey<String> SLOT_TAB = SlotKey.of("_r/0/slot_tab", String.class);

    @Override
    public SettingsModel initialModel() {
        return new SettingsModel("Player", true, 80f, "general");
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

## 3. Asynchronous Texture Streaming (`TextureRegistry`)

To show dynamic PNGs (e.g. Discord avatars or minimaps):
1. Register PNG bytes into `TextureRegistry`:
   ```java
   String hash = textureRegistry.register(pngBytes, 128, 128);
   ```
2. In your UI view:
   ```java
   t.add(new VImage("avatar", UiLayout.DEFAULT, "net-" + hash, "avatar-placeholder", Scaling.fit))
    .size(64f, 64f);
   ```
3. When opening the dialog, call:
   ```java
   textureRegistry.ensureDelivered(player.con, hash);
   ```
   The client automatically displays the placeholder and swaps it for the real texture as soon as bytes stream over `TextureStream`!

---

## 4. Headless Unit Testing in JUnit 5

No Mindustry server or mocks required:
```java
@Test
void testTabSelectionPatchesSlot() {
    var screen = new SettingsScreen();
    var initial = screen.initialModel();

    var result = screen.update(initial, new SelectTab("audio"), null);

    assertThat(result.nextState().activeTab()).isEqualTo("audio");
    assertThat(result.dirtySlots()).containsExactly(SettingsScreen.SLOT_TAB);
    assertThat(result.requiresFullRerender()).isFalse();
}
```
