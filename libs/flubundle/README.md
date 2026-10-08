# FluBundle
[![Javadoc](https://img.shields.io/badge/JavaDoc-Online-green)](https://osp54.github.io/FluBundle/javadoc/)
## Installation
Java 25 is required.
### 1. Add gradle repository
```groovy
repositories {
    maven { url "https://maven.x-core.org/releases" }
}
```
### 2. Add dependency
```groovy
dependencies {
    implementation "com.ospx:flubundle:2.0.0"
}
```

### Publishing
- Snapshots are published to `https://maven.x-core.org/snapshots` on every non-PR push.
- Releases are published to `https://maven.x-core.org/releases` when a GitHub Release is published.
- Gradle repository names are `xcoreRepositorySnapshots` and `xcoreRepositoryReleases`.

## Usage
```java
Bundle bundle = new Bundle();// or Bundle.INSTANCE for global usage
bundle.addSource(ExampleMod.class);// gets the bundles from mod classpath bundles folder

bundle.format(Locale.ENGLISH, "hello-user", Args.of("userName", "Billy")); // Hello, Billy!

Text text = Text.of("hello-user", Args.of("userName", "Billy")); // a message to render later
text.render(bundle, Locale.ENGLISH);
```

Upgrading from 1.x: see [the migration guide](docs/migrating-to-2.0.md).

### Players and selected languages
```java
// Once, in the plugin that stores the player's chosen language:
bundle.setLocaleResolver(player -> selectedLanguage(player)); // null -> client locale

// Everywhere else (any plugin sharing the bundle):
Messenger messenger = Messenger.of(bundle);
messenger.to(player).send("hexed_round_started");
messenger.to(player).announce("countdown", Args.of("seconds", 5));
messenger.all().send("server-restart", Args.of("seconds", 10)); // formatted once per locale
messenger.team(team).toast(Iconc.warning, "core-under-attack");
messenger.filter(p -> p.admin).popup(text, Popup.at(Align.top).duration(5f));
messenger.to(players).deliver(text, (player, message) -> customTransport(player, message));
```

### Introspection
```java
bundle.has("menu-title");                 // any locale
bundle.has(locale, "menu-title");         // with fallback chain
bundle.variables("kick-reason");          // {"player", "reason"}
bundle.formatAttribute(locale, "menu", "title", Args.empty());
```

### Diagnostics
- Render errors (missing `$variable`, failing function) are logged once per key and locale;
  override with `setFormatErrorHandler(...)`.
- `setMissingKeyPolicy(...)` decides what a missing key renders as: `returnKey()` (default),
  `logOnce()`, `bracketed()` (`⟦key⟧`), `throwing()` (tests).
- Loading a key that another source already defined logs the override.

### Build-time checks
```java
@Test
void bundles() {
    FtlCompiler.check(Path.of("src/main/resources/bundles"));
    LocaleConsistencyChecker.check(Path.of("src/main/resources/bundles"), "en").assertSuccess();
}
```
`LocaleConsistencyChecker` fails when a translation uses a `$variable` the English message does not,
and warns about dropped variables, orphan keys and untranslated keys.

### Key constants
`KeyConstantsGenerator` turns the base locale into a class of `String` constants, so a renamed key
breaks compilation:
```kotlin
val generateBundleKeys by tasks.registering(JavaExec::class) {
    val output = layout.buildDirectory.dir("generated/sources/bundleKeys")
    inputs.dir("src/main/resources/bundles")
    outputs.dir(output)
    classpath = configurations.compileClasspath.get()
    mainClass = "com.ospx.flubundle.compiler.KeyConstantsGenerator"
    args("src/main/resources/bundles", "org.example.i18n", "BundleKeys", output.get().asFile.path, "en")
}
sourceSets.main { java.srcDir(generateBundleKeys) }
```
```java
messenger.to(player).send(BundleKeys.HEXED_ROUND_STARTED);
```

## Features
- Locale normalization for codes like `en-US`, `en_US`, and `EN_us`
- Configurable locale aliases via `addLocaleAlias(...)`
- Built-in fallback chain: exact locale -> language locale -> default locale -> missing key policy
- Immutable `Localizer` for locale-bound formatting, `Messenger`/`Audience` for delivery to players
- `LocaleResolver` for player-selected languages shared by all plugins
- Functions and formatters can be registered at any time, also after sources are loaded
