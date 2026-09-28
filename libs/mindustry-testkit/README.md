# Mindustry Testkit (`mindustry-testkit`)

> A deterministic, headless testing toolkit and client simulator for Mindustry v160 plugins running on Java 25.

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)
[![Java](https://img.shields.io/badge/Java-25-orange.svg)](https://openjdk.org/projects/jdk/25/)
[![Mindustry](https://img.shields.io/badge/Mindustry-v160-green.svg)](https://github.com/Anuken/Mindustry/releases/tag/v160)

---

## Overview

`mindustry-testkit` provides test-only infrastructure for testing reactive, server-driven UI, async events, and network lifecycle in Mindustry server plugins without real players, graphics contexts, Xvfb, or unpredictable thread sleeps.

---

## Architecture & Components

The toolkit is divided into three modules:

### 1. `core` (`org.xcore.testkit:core`)

Pure Java library with zero dependencies on Mindustry or Arc.

- **`DeterministicQueue`**: Single-threaded, explicitly stepped task queue adhering to Arc's snapshot-drain turn model (`runTurn()`). Tasks scheduled during an active turn wait deterministically for the next turn.

### 2. `fixtures` (`org.xcore.testkit:fixtures`)

Headless World and Entity simulation fixtures for server-side plugin tests (commands, anti-grief action filters, spatial queries, team cores, and packet verification) without launching live server sockets or graphics contexts:

- **`HeadlessWorld`**: Managed, `AutoCloseable` environment providing an initialized `Vars.world` grid, `GameState`, `NetServer`, and `Groups`. Supports spatial queries, floor and block mutation, team cores with multi-tile footprint linking, command routing, and action filtering. Use `builder().generating(true)` for map generation, terrain painting, and layout planning (see below).
- **`MockPlayer`**: Simulated player entity wrapping `mindustry.gen.Player` backed by an in-memory `MockNetConnection`. Features builders, coordinate/tile positioning, chat, and command dispatch.
- **`MockNetConnection`**: Headless `NetConnection` recording all outgoing `Call.*` RPC packets (`sendMessage`, `announce`, `infoMessage`, every `infoPopup` variant, `warningToast`), player kicks (`kick(String)` / `kick(KickReason)`), and stream chunks without socket I/O.
- **`MockNet`**: Headless `Net` reporting `server()`/`active()`. Holds a connection registry, so broadcast `Call.*` overloads (the ones taking no `NetConnection`) reach every registered `MockPlayer` exactly as they would on a live server.
- **`HeadlessContent`**: Thread-safe base content loader caching vanilla blocks, units, and items once per JVM to guarantee sub-millisecond test fixture setup.
- **`HeadlessWorldExtension` & `@WithHeadlessWorld`**: JUnit 5 Jupiter extension for automated fixture lifecycle management and parameter resolution.

#### World generation mode

`Tile.setFloor` and `Tile.setBlock` consult `Vars.world.isGenerating()`. Without generation mode, any map generator, terrain painter, or layout planner throws a `NullPointerException` on a fixture world. Enable it explicitly:

```java
try (HeadlessWorld world = HeadlessWorld.builder()
        .dimensions(512, 512)
        .generating(true)
        .defaultFloor(Blocks.stone)
        .build()) {
    // bulk authoring writes now succeed without emitting TileChange events
    world.fillFloor(Blocks.sand);
}
```

When the world is created by `@WithHeadlessWorld` (annotation-driven lifecycle) there is no builder to configure, so flip the flag in `@BeforeEach`:

```java
@ExtendWith(HeadlessWorldExtension.class)
@WithHeadlessWorld
class GeneratorTest {
    @BeforeEach
    void enterGeneratingMode(HeadlessWorld world) {
        world.world().setGenerating(true);
    }
}
```

#### Popup transcripts

Mindustry splits informational popups across five packets, and `Call.infoPopup(con, ...)` sends a *different* one than `Call.infoMessage(con, ...)`. `MockNetConnection` records all of them into a single ordered transcript, so assertions no longer need to reflect into private packet fields:

```java
Call.infoPopup(player.con(), "Round 3 starting", 6f, Align.left, 0, 0, 0, 0);

player.infoPopups();          // ["Round 3 starting"] - every variant, in wire order
player.lastInfoPopup();       // "Round 3 starting"
player.lastInfoPopupPacket(); // InfoPopup record: message, duration, align, id, variant
player.infoMessages();        // InfoMessageCallPacket texts only
```

`MockNetConnection.InfoPopup` is a record shared by all variants; `MockNetConnection.PopupVariant` distinguishes `MESSAGE`, `POPUP`, `RELIABLE`, `KEYED`, and `KEYED_RELIABLE`. Popups sent with an empty message are recorded too, which is how services signal "clear this popup".

### 3. `ui` (`org.xcore.testkit:ui`)

Client simulation, transport wire snapshots, and actual-client oracle tests.

- **`HeadlessMenuClient`**: Semantic stand-in for the Mindustry client menu registry (`MenuDialog` + `Menus`). Reproduces exact recorded client semantics:
  - Window display and replacement (`show` with `hidePrevious`);
  - Outbox queue for client responses (`MenuChoose`);
  - `wasHidden` cancellation suppression (closing or replacing a dialog after a button click does not emit cancel);
  - Token-bound window lifecycle.
- **`DeterministicUiLoop`**: Orchestrates two isolated FIFO transport queues (`serverToClient` and `clientToServer`) alongside a snapshot-drain `serverPost` queue. Enables exact step-by-step control over network propagation delays and interleaved async completions.
- **`UiSnapshot`**: Immutable wire copy of `NodeBuilder<?>` trees captured at send time using Mindustry's native binary codec (`builder.write(Writes)` / `NodeBuilder.read(Reads)`). Completely eliminates mutable object reference leaks between server and client.
- **`UiWireMessage` & `UiTranscript`**: Sealed wire message records (`Show`, `Update`, `Hide`, `Choose`) and an append-only transcript logging every transport step for deterministic test assertions.
- **Actual-Client Oracle (`ActualMenusOracleTest`, `ActualDialogHideTest`)**: Executes the **real** `mindustry.ui.Menus` and `arc.scene.ui.Dialog` under Arc's official `MockGL20`, `MockGraphics`, `MockAudio`, and `MockApplication` in a plain JVM without rendering. Proves exact behavioral parity between `HeadlessMenuClient` and real client bytecode.

---

## Installation

### Gradle (Kotlin DSL)

Add the XCore snapshot repository and include testkit in your test scope:

```kotlin
repositories {
    mavenCentral()
    maven("https://maven.x-core.org/snapshots")
    maven("https://maven.x-core.org/releases")
}

dependencies {
    testImplementation("org.xcore.testkit:fixtures:0.1.0-SNAPSHOT")
    testImplementation("org.xcore.testkit:ui:0.1.0-SNAPSHOT")
    // or testImplementation("org.xcore.testkit:core:0.1.0-SNAPSHOT") for core queue utilities
}
```

`fixtures` pulls in `core` transitively and declares Mindustry/Arc as `compileOnly`, so it never
embeds game engine classes. Pair it with AssertJ:

```kotlin
testImplementation("org.assertj:assertj-core:3.27.7")
```

### Local Composite Build

During development, you can consume testkit directly from source without publishing.

Ad-hoc, without touching the consumer's build files:

```bash
./gradlew --include-build ../mindustry-testkit test
```

For a permanent, opt-in switch, add a property-guarded `includeBuild` to the consumer's `settings.gradle.kts` so CI keeps resolving published artifacts by default:

```kotlin
if (providers.gradleProperty("xcoreTestkitFromSource").orNull.toBoolean()) {
    includeBuild(file("../mindustry-testkit"))
}
```

```bash
./gradlew test -PxcoreTestkitFromSource=true
```

---

## Verification & Parity Guarantees

Unit tests in `mindustry-testkit` verify both internal simulation logic and exact parity against real Mindustry bytecode:

1. **Window Replacement Parity**: Replay of replacement sequences proves identical cancellation behavior:
   - Window replacement before a click triggers synchronous cancellation carrying the old window's token.
   - Button click marks the dialog as hidden, suppressing subsequent replacement cancellation.
   - Dismissing the replacement window emits cancellation with the fresh window token.
2. **Artifact Fingerprinting**: Runtime classpath verification asserts exact SHA-256 fingerprints of loaded Mindustry/Arc JARs (`Menus.class` and `Core.class`) to guard against Gradle resolution and cache drift.

---

## Build

```bash
./gradlew clean test assemble
```

Dependencies on Mindustry and Arc in `ui` are declared as `compileOnly` and `testImplementation`, ensuring that `ui.jar` remains pure test utilities with zero embedded game engine classes.
