# Flubundle 1.5 & Fluent 2.0 Localization Improvement Plan

## 1. Context & Objectives
Upgrade `flubundle` (1.4 -> 1.5) and `fluent-base` (2.0.0-xcore -> 2.0.1-xcore) to enhance FTL localization quality across the Mindustry XCore ecosystem (`XCore-plugin`, `TileLogger`, `aethercore-plugin`, `xcore-sentinel`, `xcore-plugin-template`).

## 2. Fluent Base Patch (`2.0.1-xcore`)
- **Bug**: In `fluent/fluent-base/src/main/java/fluent/bundle/FluentFunctionRegistry.java`, `hasCustoms()` had:
  ```java
  return !customExact.isEmpty() && !customList.isEmpty();
  ```
  This incorrectly prevented exact-only custom formatters from executing when no subtype formatters were present.
- **Fix**:
  ```java
  return !customExact.isEmpty() || !customList.isEmpty();
  ```
- **Tests**: Add regression tests for exact-only, subtype-only, exact winning over subtype, and fallback.
- **Version**: Publish as `net.xyzsd.fluent:fluent-base:2.0.1-xcore` to `https://maven.x-core.org/releases`.

## 3. Flubundle 1.5 Architecture

### A. Lifecycle & Registration API
- `FluentFunctionRegistry` is immutable.
- `Bundle` uses a startup/freeze lifecycle:
  - Methods:
    - `registerFunction(FluentFunctionFactory<?> factory)`
    - `registerFunctions(Collection<FluentFunctionFactory<?>> factories)`
    - `<T> registerFormatterExact(Class<T> type, BiFunction<T, Scope, String> formatter)`
    - `<T> registerFormatter(Class<T> supertype, BiFunction<T, Scope, String> formatter)`
  - Registration is only allowed **before** the first resource/source is added. If called after `addSource`, throws `IllegalStateException("Functions and formatters must be registered before adding bundle sources")`.
  - Also support `Bundle.builder()` pattern or fluent chained methods on `Bundle`.

### B. Standard Functions
- Include `DefaultFunctionFactories.allNonImplicits()` by default:
  - `CASE($text, style: "upper"|"lower")`
  - `COUNT($list)`
  - `NUMSORT`, `STRINGSORT`
  - `ABS`, `OFFSET`, `SIGN`
  - `TEMPORAL`
  - `BOOLEAN`

### C. Built-in Mindustry Functions
1. **`STRIP($text)`**:
   - Takes any `FluentValue<?>` (including `FluentCustom<Player>`), formats it via implicit formatting first (`scope.registry().implicitFormat(val, scope)`), then applies `arc.util.Strings.stripColors(...)`.
   - Passes `FluentError` through.
   - Stateless, canCache = true.
2. **`COLOR($text, color: "accent")`**:
   - Wraps text in `[{color}]{text}[]`.
   - Validates `color` option has no brackets `[` or `]`.
   - Defaults to `accent`.
3. **`DURATION($time, style: "compact"|"timer", unit: "seconds"|"millis")`**:
   - Numeric input (or parseable number string).
   - `unit`: `"seconds"` (default), `"millis"` (floored/truncated).
   - Preserves negative signs (`-00:05`, `-5s`).
   - `style: "timer"`: `mm:ss` or `hh:mm:ss` or `d:hh:mm:ss`.
   - `style: "compact"`:
     - `en`: `1d 2h 3m 4s`
     - `ru`: `1д 2ч 3м 4с`
     - `uk` / `be`: `1д 2г 3хв 4с`
   - **Crucial Rule**: Use `DURATION` for standalone durations and timers. Do NOT use `DURATION` for inflected grammatical prose (e.g., "через 1 секунду" / "через 5 секунд"). Inflected prose must use Fluent select expressions.

### D. Built-in Implicit Formatters
- `Player.class` (exact) -> `player.name`
- `Team.class` (exact) -> `team.name`
- `UnlockableContent.class`: Defer or do not use global `content.localizedName` if server locale does not match recipient locale.

## 4. FTL Content & Pluralization Conventions
- **Slavic languages (`ru`, `uk`, `be`)**:
  Always use all 4 categories with `*[other]` as fallback:
  ```ftl
  { $count ->
      [one] { $count } секунда
      [few] { $count } секунды
      [many] { $count } секунд
     *[other] { $count } секунды
  }
  ```
- **English (`en`)**:
  ```ftl
  { $count ->
      [one] { $count } second
     *[other] { $count } seconds
  }
  ```
- Keep placeholder sets strictly identical across all locales for any given key to satisfy `LocalizationPlaceholderConsistencyTest`.

## 5. Phased Execution Plan
1. **Phase 1 (fluent)**: Fix `hasCustoms()` in `fluent-base`, add tests, update version to `2.0.1-xcore`, tag and publish release to `maven.x-core.org/releases`.
2. **Phase 2 (flubundle)**:
   - Bump dependency to `fluent-base:2.0.1-xcore`.
   - Bump `flubundle` to `1.5`.
   - Implement registration API, standard functions, `STRIP`, `COLOR`, `DURATION`, and `Player`/`Team` formatters.
   - Comprehensive unit tests.
   - Tag and publish `flubundle:1.5` to `maven.x-core.org/releases`.
3. **Phase 3 (Consumers - Pilot `XCore-plugin`)**:
   - Bump `flubundle = "1.5"`.
   - Update `.ftl` files with proper CLDR plurals (`[one]`, `[few]`, `[many]`, `*[other]`).
   - Run `LocalizationPlaceholderConsistencyTest` and plugin test suite.
   - Commit and push.
4. **Phase 4 (Consumers - Ecosystem)**:
   - Apply updates to `TileLogger`, `aethercore-plugin`, `xcore-sentinel`, `xcore-plugin-template`.
   - Verify all tests and CI builds.
