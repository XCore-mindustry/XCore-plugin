pluginManagement {
    includeBuild("build-logic")
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

rootProject.name = "xcore"

include(
    ":libs:flubundle",
    ":libs:cloud-mindustry:lib",
    ":libs:cloud-mindustry:example-plugin",
    ":libs:xcore-ui",
    ":libs:mindustry-testkit:core",
    ":libs:mindustry-testkit:ui",
    ":libs:mindustry-testkit:fixtures",
    ":protocol",
    ":plugins:xcore",
)
