# cloud-mindustry

[![Build](https://github.com/XCore-mindustry/cloud-mindustry/actions/workflows/build.yml/badge.svg)](https://github.com/XCore-mindustry/cloud-mindustry/actions/workflows/build.yml)

This is a standalone implementation of the [Incendo Cloud v2](https://github.com/Incendo/cloud) bridge for Mindustry.

The project is based on the integration found in [Xpdustry's Distributor](https://github.com/xpdustry/distributor), but stripped down to work as a simple library without requiring a specific plugin framework.

## What's inside

- Hooks into Mindustry `CommandHandler` using Cloud v2 (supports both annotations and builders).
- Compatible with `Vars.netServer.clientCommands` (players) and `ServerControl` (console).
- Customizable permission logic and command conflict resolution.
- Target selectors (`@a`, `@p`, `@s`, `@r`, `@e[...]`) for players and units.
- Parsers for `Player`, `Team`, `UnitType`, `Block`, `Item`, `Liquid`, `StatusEffect` and offline
  `Administration.PlayerInfo` out of the box.
- An opt-in help renderer with permission filtering and pagination.
- Every error message is a Cloud caption, so it can be localized.
- No mandatory dependencies on translation engines or external permission systems.

## Setup

### Gradle (Kotlin DSL)

1. Add the XCore Maven repositories:
```kotlin
repositories {
    mavenCentral()
    maven("https://maven.x-core.org/releases")
    maven("https://maven.x-core.org/snapshots")
}
```

2. Add the library:
```kotlin
dependencies {
    implementation("org.xcore:cloud-mindustry:0.4.0")
}
```

## Basic Examples

### 1. Setup the manager
Pass the target `CommandHandler` (client or server) to the manager.

```java
import org.xcore.cloud.mindustry.MindustryCommandManager;
import mindustry.Vars;

public class MyPlugin extends Plugin {
    @Override
    public void init() {
        // For players
        var mgr = MindustryCommandManager.create(Vars.netServer.clientCommands);
        
        // For server console
        // var serverMgr = MindustryCommandManager.create(ServerControl.instance.handler);
    }
}
```

### 2. General settings
Set up your prefix and permission logic before registering any commands.

```java
import org.xcore.cloud.mindustry.ConflictStrategy;

// Define what happens if a command name is already taken
mgr.setConflictStrategy(ConflictStrategy.PREFIX); 
mgr.setCommandPrefix("myplugin"); // usage: /myplugin:command

// console and Mindustry admins get everything
mgr.setPermissionChecker((sender, permission) -> sender.isAdmin());
```

### 3. Adding commands
Example using the Cloud Builder API:

```java
import static org.incendo.cloud.parser.standard.StringParser.greedyStringParser;

mgr.command(mgr.commandBuilder("broadcast", "bc")
    .permission("myplugin.broadcast")
    .required("message", greedyStringParser())
    .handler(ctx -> {
        String msg = ctx.get("message");
        Call.sendMessage("[Gold][Broadcast] " + msg);
    })
);
```

### 4. Customizing messages (Localization)
The library doesn't include a translation system. Every message it sends — Cloud's standard errors,
selector errors and the Mindustry parser errors — is a caption, so hook your own translations into the
caption registry. Return `null` for captions you don't translate so the English defaults still apply.

```java
mgr.captionRegistry().registerProvider((caption, sender) -> {
    String locale = sender.isPlayer() ? sender.player().locale : "en";
    return MyBundle.getOrNull(caption.key(), locale);
});
```

Library caption keys live in `SelectorCaptionKeys` and `MindustryCaptionKeys`; Cloud's own keys are in
`StandardCaptionKeys`.

### 5. Errors
The manager installs Cloud's default exception handlers, so:

- failures of the command handler are logged and the sender sees the generic `exception.unexpected`
  caption — internal exception messages are never shown to players;
- an exception thrown from a command handler, preprocessor or postprocessor is unwrapped, so a handler
  you register for its type receives it:

```java
mgr.exceptionController().registerHandler(MyCommandException.class,
        ctx -> ctx.context().sender().sendMessage(ctx.exception().getMessage()));
```

Override `sendErrorMessage(sender, message)` in a subclass to change how error messages are styled.

### 6. Mindustry types
`Player`, `Team`, `UnitType`, `Block`, `Item`, `Liquid` and `StatusEffect` arguments work out of the box,
with annotations and with the builder API:

```java
import org.xcore.cloud.mindustry.parser.MindustryParsers;

mgr.command(mgr.commandBuilder("spawn")
    .required("type", MindustryParsers.unitType())
    .required("team", MindustryParsers.team())   // base teams; anyTeam() for all 256
    .handler(ctx -> { /* ... */ }));
```

With annotations, the `MindustryCommandManager.ALL_TEAMS` parser parameter opens a `Team` argument up to
every team id.

### 7. Target selectors
`Player`, `SinglePlayerSelector`, `MultiplePlayerSelector`, `SingleUnitSelector` and `MultipleUnitSelector`
arguments accept selectors such as `@s`, `@p`, `@a[team=crux,distance=..20]` or `@e[type=dagger,limit=5]`,
as well as player names and `#id`s.

Restrict them with `@DenySelectors` / `@AllowedSelectors` after calling
`mgr.registerSelectorAnnotations(annotationParser)`. On a method they apply to every selector argument of
the command; on a parameter only to that argument:

```java
@Command("give <from> <to>")
public void give(MindustrySender sender,
                 @Argument("from") @DenySelectors(reason = "name the giver") Player from,
                 @Argument("to") @AllowedSelectors(SelectorKind.SELF) SinglePlayerSelector to) { ... }
```

A `Player` argument resolves its selector while parsing, before the command is known, so
command-level restrictions are only checked after that resolution has succeeded. Put the annotation
on the `Player` parameter itself to refuse selectors before anything is resolved.

With the builder API pass `SelectorRestrictions` to `TargetSelectorParsers`, e.g.
`TargetSelectorParsers.singlePlayerSelector(mgr.selectorEngine(), SelectorRestrictions.allow(SelectorKind.SELF))`.

### 8. Player-only commands
`@PlayerOnly` on a command method, or on a class to cover all of its commands, refuses anyone who is not
an in-game player with the `mindustry.sender.player_required` caption. It works with a custom sender type
too, because the check goes through the manager's sender mapper.

```java
mgr.registerMindustryAnnotations(annotationParser); // selector annotations + @PlayerOnly

@PlayerOnly
@Command("home")
public void home(MindustrySender sender) { /* sender.player() is not null here */ }
```

With the builder API: `.meta(MindustryCommandManager.PLAYER_ONLY, true)`.

The check runs once the command is known, so input that does not parse reports its parse error first.
It is not a permission: combine it with `.permission(...)` as usual.

### 9. Help
Every Cloud command is registered in Arc with the parameters `[args...]`, so the vanilla `/help` cannot show
real usage. `MindustryHelp` renders Cloud's own help instead. It registers nothing: add a help command
yourself and call it.

```java
var help = new MindustryHelp<>(mgr, 8); // 8 entries per page

mgr.command(mgr.commandBuilder("help")
    .optional("query", greedyStringParser())
    .handler(ctx -> help.sendQuery(ctx.sender(), ctx.getOrDefault("query", ""), 1)));
```

- A sender sees only commands they have permission for; `@PlayerOnly` commands are hidden from the console.
- `sendQuery` shows the usage of the command the query names, the variants below it, or the commands
  starting with it. `sendIndex(sender, page)` lists everything.
- After a `PREFIX` collision the published name (`/myplugin:command`) is shown; skipped commands are not.
- A third constructor argument adds your own visibility filter on top of these.
- `includeLegacyCommands(predicate)` also lists the Arc commands that were not registered through Cloud.
  They have no Cloud permission, so your predicate alone decides who sees them.
- Every line is a caption (`mindustry.help.*` in `MindustryCaptionKeys`).

### 10. Offline players
An `Administration.PlayerInfo` argument (`MindustryParsers.playerInfo()`) finds a player the server has a
record of, online or not: by UUID, by any name they have used, by `#id` if they are online, and from the
console by IP.

- A name or IP that matches several records is an error; the parser never picks one.
- The argument is one token, so a name with spaces has to be given as a UUID or `#id`.
- Suggestions list online players only.

### 11. Threads
Mindustry's game state belongs to one thread. Each manager has a `SimulationExecutor` for it:
`MindustryCommandManager.create(handler)` uses the application's main thread and runs the whole command
pipeline (parsing, suggestions, handlers, error messages) there. A command issued from that thread, as Arc
does, is still handled synchronously.

Selectors and the `PlayerInfo` parser refuse to run anywhere else: they throw instead of waiting for the
game thread. If your handler continues on a worker thread, go back before touching game state:

```java
CompletableFuture.supplyAsync(this::loadFromDatabase)
    .thenAcceptAsync(data -> target.resolve(sender).sendMessage(data), mgr.simulationExecutor());
```

To use your own coordinator, pass the executor to the constructor as well:

```java
var simulation = SimulationExecutor.forApplication(Core.app);
var mgr = new MindustryCommandManager<>(handler,
        ExecutionCoordinator.coordinatorFor(simulation), senderMapper, simulation);
```

A coordinator that moves the pipeline to other threads is still allowed for work that does not touch
game state, but selector and `Player` arguments will fail under it.

## Migrating from 0.3

- `registerMindustryAnnotations(annotationParser)` registers every annotation of the library.
  `registerSelectorAnnotations` still registers the selector annotations only, so `@PlayerOnly` has no
  effect until you switch to the new method.
- **Breaking:** resolving a selector off the game thread no longer blocks until the game thread has done
  it; it throws `IllegalStateException`. Hop through `mgr.simulationExecutor()` first (see *Threads*).
- **Breaking:** `MindustryCommandManager.create` now coordinates the whole pipeline on the game thread.
  The three-argument constructor still takes your coordinator as is.
- `SelectorResolutionBridge` is deprecated, unused by the library and will be removed in the next release.
  The game thread is no longer guessed from thread names or set by whoever constructs a manager last.
- `SpatialSelectorEngine` keeps no state between calls; `new SpatialSelectorEngine(simulationExecutor)`
  binds it to a thread explicitly.

## Migrating from 0.2

- Selector exceptions now extend `SelectorException` (with `caption()` / `captionVariables()`);
  `SelectorSyntaxException` is no longer an `IllegalArgumentException`. Parse-time selector failures reach
  exception handlers as `ArgumentParseException` caused by `SelectorParseException` (a Cloud `ParserException`).
- The default error handlers no longer print `"[scarlet]Error: " + exception.getMessage()`; see *Errors* above.
- `SelectorCaptionProvider` no longer answers captions it doesn't know, which used to blank out
  Cloud's standard messages.
- `ConsoleSender` strips Mindustry color tags before logging.
- `SelectorGuard.LAST_SELECTOR_SPEC_KEY` is deprecated in favour of `SelectorGuard.selectorSpecs(command, context)`.
