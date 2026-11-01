allprojects {
    group = "org.xcore"
    version = "0.4.1"

    repositories {
        mavenCentral()
        maven("https://jitpack.io")
        maven("https://raw.githubusercontent.com/Zelaux/MindustryRepo/master/repository")
        maven("https://maven.x-core.org/releases")
    }

    configurations.all {
        resolutionStrategy.eachDependency {
            if (requested.group == "com.github.Anuken.Arc") {
                useVersion(libs.versions.mindustry.get())
            }
        }
    }
}
