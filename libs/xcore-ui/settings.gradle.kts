rootProject.name = "xcore-ui"

pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        maven("https://jitpack.io")
    }
}

// Local development escape hatch: resolve `org.xcore.testkit:*` from the sibling
// checkout instead of the published snapshot, so testkit changes can be exercised
// without publishing. CI must NOT set this and keeps resolving published artifacts.
//
//   ./gradlew test -PxcoreTestkitFromSource=true
if (providers.gradleProperty("xcoreTestkitFromSource").orNull.toBoolean()) {
    val testkit = file("../mindustry-testkit")
    require(testkit.isDirectory) { "xcoreTestkitFromSource=true but $testkit does not exist" }
    includeBuild(testkit)
}
