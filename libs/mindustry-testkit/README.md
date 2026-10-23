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

- **`DeterministicQueue`**: Single-threaded, explicitly stepped task queue adhering to Arc's snapshot-drain turn model (`runTurn()`). Tasks scheduled during an active turn wait deterministically for the next turn. It is an `Executor`, so it replaces a hand-rolled `ArrayDeque<Runnable>` main thread; `runUntilIdle()` runs turns until nothing is pending and fails on a task that re-posts itself forever; `size()`/`isEmpty()` support assertions.

### 2. `fixtures` (`org.xcore.testkit:fixtures`)

Headless World and Entity simulation fixtures for server-side plugin tests (commands, anti-grief action filters, spatial queries, team cores, and packet verification) without launching live server sockets or graphics contexts:

- **`HeadlessWorld`**: Managed, `AutoCloseable` environment providing an initialized `Vars.world` grid, `GameState`, `NetServer`, and `Groups`. Supports spatial queries, floor and block mutation, team cores with multi-tile footprint linking, command routing, and action filtering. Use `builder().generating(true)` for map generation, terrain painting, and layout planning (see below).
- **`MockPlayer`**: Simulated player entity wrapping `mindustry.gen.Player` backed by an in-memory `MockNetConnection`. Features builders, coordinate/tile positioning, chat, and command dispatch.
- **`MockNetConnection`**: Headless `NetConnection` recording all outgoing `Call.*` RPC packets (`sendMessage`, `announce`, `infoMessage`, every `infoPopup` variant, `warningToast`), player kicks (`kick(String)` / `kick(KickReason)`), and stream chunks without socket I/O. `onSend(listener)` hands every recorded packet onward, in wire order — e.g. menu packets into `DeterministicUiLoop.sendPacket`.
- **`MockNet`**: Headless `Net` reporting `server()`/`active()`. Holds a connection registry, so broadcast `Call.*` overloads (the ones taking no `NetConnection`) reach every registered `MockPlayer` exactly as they would on a live server.
- **Custom packets and redirects**: `MockNetConnection` also records `Call.clientPacket*` / `clientBinaryPacket*` (`clientPackets()`, `clientPackets(type)`) and `Call.connect` (`redirects()`, `lastRedirect()`); `InfoPopup` carries the popup margins. `MockPlayer.sendServerPacket(type, contents)` / `sendServerBinaryPacket` send a packet from the player's client through the real `NetServer` dispatch, so handlers registered with `netServer.addPacketHandler` run. Together they replace `mockStatic(Call.class)`.
- **`HeadlessWorld.join(...)` / `leave(...)`**: connect and disconnect a player the way a live server does — `NetServer.connectConfirm` (`PlayerConnectionConfirmed`, `PlayerJoin`) and `NetServer.onDisconnect` (`PlayerLeave`) — so the plugin's own join/leave handlers run. `addPlayer`/`removePlayer` stay the silent variants.
- **`ManualTimer`** (`HeadlessWorld.timer()` or `ManualTimer.install()`): Arc's `Timer` on virtual time. While installed, every `Timer.schedule(...)` waits until the test calls `advance(seconds)`; repeating tasks run once per interval crossed, cancelled ones never. No sleeps, no `mockStatic(Timer.class)`, no reflection into `Timer.Task` fields.
- **`EventRecorder`** (`HeadlessWorld.recordEvents(type)` or `EventRecorder.record(type)`): records every `Events.fire` of a type — `events()`, `count()`, `last()`.

```java
@WithHeadlessWorld
class RoundTest {
    @Test
    void countdownStartsTheRound(HeadlessWorld world) {
        var timer = world.timer();
        var gameOvers = world.recordEvents(EventType.GameOverEvent.class);
        MockPlayer alice = world.join("Alice", Team.sharded);   // plugin's PlayerJoin handler runs
        world.join("Bob", Team.sharded);

        timer.advance(15f);                                     // the 15 s countdown task runs
        alice.sendServerPacket("fooCheck", "");                 // client -> server custom packet
        assertThat(alice.clientPackets("fooCheck")).containsExactly("2.0");
        world.leave(alice);                                     // plugin's PlayerLeave handler runs
    }
}
```
- **`HeadlessContent`**: Thread-safe base content loader caching vanilla blocks, units, and items once per JVM to guarantee sub-millisecond test fixture setup.
- **`@WithHeadlessWorld` & `HeadlessWorldExtension`**: JUnit 5 Jupiter extension for automated fixture lifecycle management and parameter resolution. The annotation registers the extension itself, so it is the only annotation a test needs; it takes `width`, `height` and `generating`, a method-level one wins over the class-level one, and `@Nested` classes use their enclosing class's.

```java
@WithHeadlessWorld(width = 64, height = 64)
class TeleportTest {
    @Test
    void movesUnit(HeadlessWorld world) {
        MockPlayer self = world.addPlayer(b -> b.name("Self").position(50f, 50f));
        Unit unit = self.spawnUnit(UnitTypes.dagger); // the unit the player now controls
    }
}
```

#### World generation mode

`Tile.setFloor` and `Tile.setBlock` fire a `TileFloorChangeEvent` / `TileChangeEvent` and update the pathfinder unless the world is generating. Bulk map authoring (map generators, terrain painters, layout planners) should run in generating mode so that hundreds of tile writes do not flood the event bus or wake the pathfinder.

Inside a `HeadlessWorld` a *single* write in non-generating mode is already safe — `Vars.headless` is `true`, so the `Vars.renderer` branch that would throw `NullPointerException` is skipped, and `Vars.pathfinder` is `null`. This option is about event and pathfinder suppression, not about making writes possible:

```java
try (HeadlessWorld world = HeadlessWorld.builder()
        .dimensions(512, 512)
        .generating(true)
        .defaultFloor(Blocks.stone)
        .build()) {
    // bulk authoring writes no longer emit TileChange events
    world.fillFloor(Blocks.sand);
}
```

When the world is created by `@WithHeadlessWorld`, set it on the annotation:

```java
@WithHeadlessWorld(generating = true)
class GeneratorTest { ... }
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
- **`UiWireMessage` & `UiTranscript`**: Sealed wire message records (`Show`, `Update`, `Hide`, `Choose`) and an append-only transcript logging every transport step for deterministic test assertions. `Show`/`Update` expose `dsl()`; `Choose` carries the form `values` the client sent and turns into the server's `MenuResult` with `toMenuResult()`; `UiWireMessage.fromPacket` maps the `Call.menuBuilder*` packets. The transcript answers `lastShow()`, `lastUpdate()`, `shows()`, `updates()`, `chooses()`, `all(Type.class)` and `last(Type.class)`.

#### Driving a menu end to end

```java
var loop = new DeterministicUiLoop();
try (var app = loop.installAsCoreApp()) {             // Core.app.post -> loop.serverPost()
    // Either a test gateway: loop.sendShow(menuId, token, hidePrevious, ui) / sendUpdate / sendHide,
    // or the plugin's real Call.menuBuilder* path through a MockPlayer:
    player.con().onSend(loop::sendPacket);
    loop.dispatchChoicesTo(player.player());           // answers -> Menus.menuBuilderChoose -> plugin listener
    // or: loop.onChoose(choose -> menuService.onMenuBuilderResult(session, choose.toMenuResult()));

    menu.open(session);
    loop.runUntilIdle();                               // deliver, answer, run server posts, until quiet
    assertThat(loop.transcript().lastShow().dsl()).contains("action:search");

    loop.client().click(menuId, "action:search", Map.of("field_help_search", "sync"));
    loop.runUntilIdle();
    assertThat(loop.transcript().lastUpdate().dsl()).contains("action:cmd:sync");
}
```

Step-by-step control (`stepServerToClient`, `stepClientToServer`, `stepServerPost`) stays available for interleaving tests; `deliverToClient()` drains only the server-to-client queue.
- **`MenuFixtures`**: Writes a `MenuBuilderCallPacket` to disk as the server sends it (`.packet`, plus a readable `.msui`), after checking that it fits a packet and survives the wire unchanged. Input for the real-client renderer below.
- **Actual-Client Oracle (`ActualMenusOracleTest`, `ActualDialogHideTest`)**: Executes the **real** `mindustry.ui.Menus` and `arc.scene.ui.Dialog` under Arc's official `MockGL20`, `MockGraphics`, `MockAudio`, and `MockApplication` in a plain JVM without rendering. Proves exact behavioral parity between `HeadlessMenuClient` and real client bytecode.

### 4. `render/` (tool, not published)

Opens server menus in the **official desktop client** under Xvfb, saves a screenshot of each on
phone, Android and desktop screens, and reports what runs off the screen, out of its dialog or out
of its button. It is the one part of the kit that needs graphics: everything above runs in a plain
JVM, so this stays a separate, opt-in tool. See [`render/README.md`](render/README.md).

```sh
./gradlew exportUiFixtures                      # in a plugin: writes .packet fixtures with MenuFixtures
../mindustry-testkit/render/render build/ui-render/packets
```

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
