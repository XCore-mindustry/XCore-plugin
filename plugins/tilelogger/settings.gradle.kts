rootProject.name = "TileLogger"

pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
        maven("https://raw.githubusercontent.com/Zelaux/MindustryRepo/master/repository")
        maven("https://www.jitpack.io")
        maven("https://maven.x-core.org/releases")
        maven("https://maven.x-core.org/snapshots")
    }
}

include("native")
