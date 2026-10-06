# cloud-mindustry

[![Build](https://github.com/XCore-mindustry/cloud-mindustry/actions/workflows/build.yml/badge.svg)](https://github.com/XCore-mindustry/cloud-mindustry/actions/workflows/build.yml)

This is a standalone implementation of the [Incendo Cloud v2](https://github.com/Incendo/cloud) bridge for Mindustry.

The project is based on the integration found in [Xpdustry's Distributor](https://github.com/xpdustry/distributor), but stripped down to work as a simple library without requiring a specific plugin framework.

## What's inside

- Hooks into Mindustry `CommandHandler` using Cloud v2 (supports both annotations and builders).
- Compatible with `Vars.netServer.clientCommands` (players) and `ServerControl` (console).
- Customizable permission logic and command conflict resolution.
- Target selectors (`@a`, `@p`, `@s`, `@r`, `@e[...]`) for players and units.
- Parsers for `Player`, `Team`, `UnitType`, `Block`, `Item`, `Liquid` and `StatusEffect` out of the box.
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
    implementation("org.xcore:cloud-mindustry:0.3.0")
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
// With OVERRIDE, deleting the root command later (mgr.deleteRootCommand) puts the replaced command back

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

## Migrating from 0.2

- Selector exceptions now extend `SelectorException` (with `caption()` / `captionVariables()`);
  `SelectorSyntaxException` is no longer an `IllegalArgumentException`. Parse-time selector failures reach
  exception handlers as `ArgumentParseException` caused by `SelectorParseException` (a Cloud `ParserException`).
- The default error handlers no longer print `"[scarlet]Error: " + exception.getMessage()`; see *Errors* above.
- `SelectorCaptionProvider` no longer answers captions it doesn't know, which used to blank out
  Cloud's standard messages.
- `ConsoleSender` strips Mindustry color tags before logging.
- `SelectorGuard.LAST_SELECTOR_SPEC_KEY` is deprecated in favour of `SelectorGuard.selectorSpecs(command, context)`.
