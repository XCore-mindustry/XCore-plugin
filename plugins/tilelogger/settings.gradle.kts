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
    }
}

include("native")

includeBuild("XCore-plugin") {
    dependencySubstitution {
        substitute(module("org.xcore:xcore-plugin")).using(project(":"))
        substitute(module("org.xcore:flubundle")).using(project(":flubundle"))
    }
}
