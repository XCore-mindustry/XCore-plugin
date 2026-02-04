# cloud-mindustry

[![Release](https://jitpack.io/v/XCore-mindustry/cloud-mindustry.svg)](https://jitpack.io/#XCore-mindustry/cloud-mindustry)
[![Build](https://github.com/XCore-mindustry/cloud-mindustry/actions/workflows/build.yml/badge.svg)](https://github.com/XCore-mindustry/cloud-mindustry/actions/workflows/build.yml)

This is a standalone implementation of the [Incendo Cloud v2](https://github.com/Incendo/cloud) bridge for Mindustry.

The project is based on the integration found in [Xpdustry's Distributor](https://github.com/xpdustry/distributor), but stripped down to work as a simple library without requiring a specific plugin framework.

## What's inside

- Hooks into Mindustry `CommandHandler` using Cloud v2 (supports both annotations and builders).
- Compatible with `Vars.netServer.clientCommands` (players) and `ServerControl` (console).
- Customizable permission logic and command conflict resolution.
- No mandatory dependencies on translation engines or external permission systems.

## Setup

### Gradle (Kotlin DSL)

1. Add JitPack to your repositories:
```kotlin
repositories {
    mavenCentral()
    maven("https://jitpack.io")
}
```

2. Add the library:
```kotlin
dependencies {
    implementation("com.github.XCore-mindustry:cloud-mindustry:0.1.0")
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

mgr.setPermissionChecker((sender, permission) -> {
    if (sender.isPlayer() && sender.player() != null) {
        return sender.player().admin; 
    }
    return true; // console gets everything
});
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
The library doesn't include a translation system by default. You can hook your own via the Caption Registry.

```java
mgr.captionRegistry().registerProvider((caption, sender) -> {
    if (sender.isPlayer()) {
        return MyBundle.get(caption.key(), sender.player().locale);
    }
    return MyBundle.getDefault(caption.key());
});
```

### 5. Adding Mindustry Parsers
Mindustry-specific parsers (like `Player` or `Team`) aren't included in the core to keep it small. You can add them yourself using the Cloud API:

```java
mgr.parserRegistry().registerParser(Player.class, ParserDescriptor.of(new MyPlayerParser(), Player.class));
```