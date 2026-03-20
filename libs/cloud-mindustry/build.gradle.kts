allprojects {
    group = "org.xcore"
    val baseVersion = "0.2.0"
    version = providers.gradleProperty("xcorePublishVersion").orElse(baseVersion).get()

    repositories {
        mavenCentral()
        maven("https://jitpack.io")
        maven("https://raw.githubusercontent.com/Zelaux/MindustryRepo/master/repository")
        maven("https://maven.x-core.org/releases")
        maven("https://maven.x-core.org/snapshots")
    }

    configurations.all {
        resolutionStrategy.eachDependency {
            if (requested.group == "com.github.Anuken.Arc") {
                useVersion(libs.versions.mindustry.get())
            }
        }
    }
}
