# XCore-UI (`xcore-ui`)

> A type-safe, reactive, server-driven UI framework for Mindustry v160 servers running on Java 25.

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)
[![Java](https://img.shields.io/badge/Java-25-orange.svg)]()
[![Mindustry](https://img.shields.io/badge/Mindustry-v160-green.svg)]()

---

## Highlights

- **Zero-Flicker Partial Updates**: Emits granular `Call.menuBuilderUpdate` calls targeting dynamic slots. Preserves client scrollbar offsets and text field focus without dialog tearing.
- **Pure Elm / MVI Architecture**: State transitions are pure functions `(Model, Event) -> UpdateResult<Model>`. Business logic is strictly decoupled from presentation.
- **Type-Safe Record Lenses (`Lens<S, V>`)**: Binds UI inputs directly to immutable Java 25 record components with method references (`State::nickname`, `State::withNickname`), completely eliminating magic string typos.
- **Dual-Source Presentation Engine**:
  - **Fluent Java 25 DSL**: Compile-time checked, zero-allocation builder with lazy FluBundle localization.
  - **Hot-Reloadable `.msui` Templates**: External DSL files reloaded in sub-milliseconds via Java NIO `WatchService` without server restarts.
- **Topological Subtree Pruning (`SlotPruner`)**: Automatically drops redundant descendant slot updates when an ancestor container is already dirty, preventing network packet collisions.
- **Content-Addressable Asset Pipeline (`TextureRegistry`)**: Streams PNGs to client atlases (`net-<hash>`) with SHA-256 deduplication, BitSet connection delivery tracking, and virtual-thread I/O.
- **100% Headless Testability**: The sealed Virtual DOM (`VNode`) runs under pure JUnit 5 in milliseconds without graphics contexts or mocks.
- **Seamless Backward Compatibility**: `LegacyMenuScreenAdapter` transparently compiles pre-v160 `MenuScreen` records into v160 `NodeBuilder<?>` trees.

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

## License

Licensed under the [MIT License](LICENSE).
