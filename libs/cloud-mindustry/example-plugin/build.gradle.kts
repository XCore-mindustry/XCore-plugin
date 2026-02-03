import com.xpdustry.toxopid.spec.ModPlatform
import com.xpdustry.toxopid.extension.anukeZelaux

plugins {
    java
    alias(libs.plugins.shadow)
    alias(libs.plugins.toxopid)
}

group = "org.xcore"
version = "1.0.0"

toxopid {
    compileVersion = libs.versions.mindustry.get()
    platforms = setOf(ModPlatform.SERVER)
}

repositories {
    mavenCentral()
    anukeZelaux()
}

dependencies {
    implementation(project(":lib"))

    compileOnly(libs.mindustry.core)
    compileOnly(libs.arc.core)
}

tasks {
    shadowJar {
        archiveFileName = "example-plugin.jar"

        relocate("org.incendo.cloud", "org.xcore.plugin.shaded.cloud")

        exclude("META-INF/maven/**")
        exclude("META-INF/gradle/**")
    }

    build {
        dependsOn(shadowJar)
    }
}
