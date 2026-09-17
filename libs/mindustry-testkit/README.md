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

The toolkit is divided into two modules:

### 1. `core` (`org.xcore.testkit:core`)

Pure Java library with zero dependencies on Mindustry or Arc.

- **`DeterministicQueue`**: Single-threaded, explicitly stepped task queue adhering to Arc's snapshot-drain turn model (`runTurn()`). Tasks scheduled during an active turn wait deterministically for the next turn.

### 2. `ui` (`org.xcore.testkit:ui`)

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
    testImplementation("org.xcore.testkit:ui:0.1.0-SNAPSHOT")
    // or testImplementation("org.xcore.testkit:core:0.1.0-SNAPSHOT") for core queue utilities
}
```

### Local Composite Build

During development, you can consume testkit directly from source without publishing:

```bash
./gradlew --include-build ../mindustry-testkit test
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
