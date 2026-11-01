rootProject.name = "cloud-mindustry"

include(":lib", ":example-plugin")

pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        maven("https://jitpack.io")
    }
}