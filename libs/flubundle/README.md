# FluBundle
[![Javadoc](https://img.shields.io/badge/JavaDoc-Online-green)](https://osp54.github.io/FluBundle/javadoc/)
## Installation
Java 17 is required.
### 1. Add gradle repository
```groovy
repositories {
    maven { url "https://n1.x-core.fun/maven/releases"}
}
```
### 2. Add dependency
```groovy
dependencies {
    implementation "com.ospx:flubundle:1.0"
}
```
## Usage
```java
Bundle bundle = new Bundle();// or Bundle.INSTANCE for global usage
bundle.addSource(ExampleMod.class);// gets the bundles from mod classpath bundles folder

bundle.format(new Locale("en"), "hello-user", 
        Map.of("userName", "Billy")) // Hello, Billy!
```