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
    implementation "com.ospx:flubundle:1.8.0"
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

bundle.format(new Locale("en"), "hello-user", 
        Map.of("userName", "Billy")) // Hello, Billy!
```

### Players and selected languages
```java
// Once, in the plugin that stores the player's chosen language:
bundle.setLocaleResolver(player -> selectedLanguage(player)); // null -> client locale

// Everywhere else (any plugin sharing the bundle):
bundle.send(player, "hexed_round_started");
bundle.announce(player, "countdown", Args.of("seconds", 5));
bundle.send("server-restart", Args.of("seconds", 10)); // formatted once per locale
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
- `DefaultValueFactory.logMissing(...)` logs each missing key once.
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

## Features
- Locale normalization for codes like `en-US`, `en_US`, and `EN_us`
- Configurable locale aliases via `addLocaleAlias(...)`
- Built-in fallback chain: exact locale -> language locale -> default locale -> default value factory
- Immutable `Localizer` and `BundleContext` helpers for locale-bound formatting and player delivery
- `LocaleResolver` for player-selected languages shared by all plugins
- Functions and formatters can be registered at any time, also after sources are loaded
