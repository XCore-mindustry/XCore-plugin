import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion

plugins {
    alias(libs.plugins.kotlin.jvm) apply false
}

allprojects {
    group = "io.github.xcore"
    version = "3.0.0"
}

subprojects {
    apply(plugin = rootProject.libs.plugins.kotlin.jvm.get().pluginId)
    apply<JavaPlugin>()

    repositories {
        mavenCentral()
        maven("https://www.jitpack.io") {
            name = "jitpack"
            mavenContent { releasesOnly() }
        }
        maven("https://oss.sonatype.org/content/repositories/snapshots/") {
            name = "sonatype-snapshots"
            mavenContent { snapshotsOnly() }
        }
    }

    dependencies {
        val implementation by configurations
        implementation(kotlin("stdlib-jdk8"))
        implementation(kotlin("reflect"))
        implementation(rootProject.libs.kotlinx.coroutines.core)
        implementation(rootProject.libs.kotlinx.coroutines.jdk8)
    }

    tasks.withType<JavaCompile> {
        sourceCompatibility = "17"
        targetCompatibility = "17"
        options.encoding = "UTF-8"
    }

    extensions.configure<KotlinJvmExtension> {
        jvmToolchain(17)
        compilerOptions {
            jvmTarget = JvmTarget.JVM_17
            apiVersion = KotlinVersion.KOTLIN_2_1
        }
    }
}
