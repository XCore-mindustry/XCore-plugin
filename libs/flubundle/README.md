# FluBundle
[![Javadoc](https://img.shields.io/badge/JavaDoc-Online-green)](https://osp54.github.io/FluBundle/javadoc/)
## Installation
Java 25 is required.
### 1. Add gradle repository
```groovy
repositories {
    maven { url "https://n1.x-core.fun/maven/releases"}
}
```
### 2. Add dependency
```groovy
dependencies {
    implementation "com.ospx:flubundle:1.3"
}
```
## Usage
```java
Bundle bundle = new Bundle();// or Bundle.INSTANCE for global usage
bundle.addSource(ExampleMod.class);// gets the bundles from mod classpath bundles folder

bundle.format(new Locale("en"), "hello-user", 
        Map.of("userName", "Billy")) // Hello, Billy!
```

## Features
- Locale normalization for codes like `en-US`, `en_US`, and `EN_us`
- Configurable locale aliases via `addLocaleAlias(...)`
- Built-in fallback chain: exact locale -> language locale -> default locale -> default value factory
- Immutable `Localizer` and `BundleContext` helpers for locale-bound formatting and player delivery
