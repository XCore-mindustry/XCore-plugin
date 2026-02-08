allprojects {
    group = "org.xcore"
    version = "0.2.0"

    repositories {
        mavenCentral()
        maven("https://jitpack.io")
        maven("https://raw.githubusercontent.com/Zelaux/MindustryRepo/master/repository")
    }

    configurations.all {
        resolutionStrategy.eachDependency {
            if (requested.group == "com.github.Anuken.Arc") {
                useVersion(libs.versions.mindustry.get())
            }
        }
    }
}