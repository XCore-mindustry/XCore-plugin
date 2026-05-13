### Refactoring Specification File

```markdown
# XCore UI Flow Refactoring Spec

## 1. Motivation & Context
The current `MenuFlow` and `RoutedMenuFlow` architecture provides excellent state management and routing capabilities. However, it requires a significant amount of boilerplate for every menu implementation. Specifically:
- `List.of(List.of(...))` structures make the UI `render` methods hard to read.
- `switch(actionId)` and `switch(promptId)` blocks inside `onAction`, `onPromptSubmit`, and `onPromptCancel` mix routing logic with business logic.
- `actionId.startsWith(...)` checks require manual substring parsing for dynamic IDs.
- Stateless menus require the creation of empty dummy `State` classes.

**Goal:** Migrate to a declarative, fluent, and routing-based approach within the flows using `BaseMenuFlow<T>` and a `MenuGrid` UI builder, drastically reducing boilerplate and improving Developer Experience (DX).

---

## 2. Core Components to Implement

### 2.1. `MenuGrid` (UI Builder)
A helper class to construct `List<List<MenuButton>>` fluently.
**Required API:**
- `row(MenuButton... buttons)`
- `rowIf(boolean condition, MenuButton... buttons)`
- `pagination(int currentPage, int totalPages, String prevAction, String nextAction, Localization loc)`
- `defaultNavigation(Session session, Localization loc)` (adds `back` if available, and `close`)
- `build()` -> returns `List<List<MenuButton>>`

### 2.2. `NoState`
A standardized empty record for stateless flows to prevent the proliferation of `MainState`, `ListState`, etc.
```java
public record NoState() {}
```

### 2.3. `BaseMenuFlow<T>`
An abstract base class implementing `RoutedMenuFlow<T>`.
**Responsibilities:**
- Manage a registry of actions: `Map<String, Consumer<MenuRenderContext<T>>>`.
- Manage prefix actions: `Map<String, BiConsumer<MenuRenderContext<T>, String>>` (payload is the suffix after the prefix).
- Manage prompts: `Map<String, Consumer<MenuPromptContext<T>>>` (submit) and `Map<String, Consumer<MenuRenderContext<T>>>` (cancel).
- Pre-register `back` and `close` default actions.
- Provide a default `createState` implementation that instantiates `T` via reflection (if `T` has a no-args constructor) or returns `currentState`.

---

## 3. Migration Execution Plan & Checklist

The refactoring MUST be executed in phases to ensure the application remains stable and testable.

### Phase 1: Core Foundation
- [ ] Create `org.xcore.plugin.ui.flow.MenuGrid` class.
- [ ] Create `org.xcore.plugin.ui.flow.NoState` record.
- [ ] Create `org.xcore.plugin.ui.flow.BaseMenuFlow<T>` abstract class.
- [ ] Implement declarative routing methods in `BaseMenuFlow` (`action()`, `actionPrefix()`, `onPrompt()`).
- [ ] Implement default `onAction`, `onPromptSubmit`, and `onPromptCancel` in `BaseMenuFlow` that delegate to the registered handlers.

### Phase 2: Stateless Menus (Low Risk)
Refactor the following flows to extend `BaseMenuFlow<NoState>` and use `MenuGrid`. Delete their custom empty state classes.
- [ ] `InformationMenu.java` (`MainFlow`, `InformationFlow`).
- [ ] `DiscordFlows.java` (`MainFlow`). Note: `LinkingFlow` has state, keep it as `BaseMenuFlow<LinkingState>`.
- [ ] `HelpFlows.java` (`HelpListFlow` - change to `NoState` but use `route.intParam` inside render; `HelpDetailsFlow`).

### Phase 3: Dynamic ID Menus (Medium Risk)
Refactor flows that use dynamic action IDs (e.g., `select:1`, `details:audit-123`). Use `actionPrefix()` to handle the string parsing automatically.
- [ ] `AuditHistoryFlows.java` (`HistoryFlow`, `DetailsFlow`). Use `actionPrefix("details:", ...)`
- [ ] `MessageFlows.java` (`InboxFlow`, `BlockedFlow`, `DetailsFlow`). Refactor `PROMPT_REPLY`, `PROMPT_COMPOSE_TARGET`, etc. using `onPrompt()`.
- [ ] `PlayerProfileFlows.java` (`PlayersFlow`, `PlayerFlow`).

### Phase 4: Complex Stateful Menus & Prompts (High Risk)
Refactor menus that heavily rely on `Session.getDraft()` and multi-step prompts.
- [ ] `PlayerSettingsFlows.java` (All flows). Migrate all `onPromptSubmit` switches to declarative `onPrompt()` calls in constructors.
- [ ] `EventDraftFlows.java` (All flows).
- [ ] `EventFlows.java` (All flows).
- [ ] `MapFlows.java` (All flows).
- [ ] `BanMenu.java` (`BanFlow`).

### Phase 5: Cleanup
- [ ] Remove unused `ACTION_*` and `PROMPT_*` string constants if they are now inlined in the `BaseMenuFlow` constructors.
- [ ] Ensure all tests pass (`./gradlew test`). Update tests if they relied on internal `MenuScreen` structures that slightly changed.

---

## 4. Conventions & Guidelines

- **Action Naming:** Use kebab-case for action IDs (e.g., `edit-name`, `toggle-major`).
- **Prefix Naming:** Prefix actions must end with a colon `:` (e.g., `select:`, `map:`). The handler payload will receive the string immediately following the colon.
- **Dependency Injection:** Dependencies should still be passed via constructors in the Flow classes, but action registration should happen immediately in the constructor body.
- **Context Wrappers:** For prompts, use a record `MenuPromptContext<T>(MenuRenderContext<T> renderContext, String text)` to pass both the context and the user input cleanly.

---

## 5. Pull Request Recommendations

### PR Title Convention
`refactor(ui): migrate menu flows to declarative BaseMenuFlow and MenuGrid`

### PR Description Template
```markdown
## Overview
Refactored the UI Flow architecture to reduce boilerplate, removing massive `switch` statements and `List.of` chains.

## Key Changes
1. **Core:** Introduced `BaseMenuFlow`, `MenuGrid`, and `NoState`.
2. **Routing:** Actions and Prompts are now registered declaratively in Flow constructors via `action()`, `actionPrefix()`, and `onPrompt()`.
3. **UI Building:** `MenuGrid` handles row construction, pagination, and default navigation.
4. **Cleanup:** Removed over 20+ empty state classes and hundreds of lines of `switch/case` boilerplate.

## Testing
- [x] Unit tests passed.
- [x] Verified prefix routing (`select:`, `details:`) works correctly.
- [x] Verified multi-step prompts (e.g., `BanMenu`, `EventDraftFlows`) execute callbacks correctly.
```
```
