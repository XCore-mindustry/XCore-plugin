import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import com.xpdustry.toxopid.extension.anukeXpdustry
import com.xpdustry.toxopid.spec.ModPlatform
import com.xpdustry.toxopid.Toxopid
import java.util.Locale
import org.gradle.nativeplatform.tasks.LinkSharedLibrary

plugins {
    java
    alias(libs.plugins.avaje.inject)
    alias(libs.plugins.shadow)
    alias(libs.plugins.toxopid)
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(libs.versions.java.get().toInt()))
}

toxopid {
    compileVersion.set(libs.versions.mindustry.get())
    runtimeVersion.set(libs.versions.mindustry.get())
    platforms = setOf(ModPlatform.SERVER)
}

repositories {
    mavenLocal()
    anukeXpdustry()
    mavenCentral()
    maven("https://maven.x-core.org/releases")
    maven("https://maven.x-core.org/snapshots")
}

dependencies {
    compileOnly(toxopid.dependencies.arcCore)
    compileOnly(toxopid.dependencies.mindustryCore)
    compileOnly(toxopid.dependencies.mindustryHeadless)

    compileOnly(libs.xcore.plugin)
    compileOnly(libs.flubundle)
    compileOnly(libs.cloud.mindustry)

    compileOnly(libs.avaje.inject)
    annotationProcessor(libs.avaje.inject.gen)
}

tasks.named<JavaCompile>("compileJava") {
    options.encoding = "UTF-8"
    options.headerOutputDirectory.set(layout.buildDirectory.dir("generated/sources/headers/java/main"))
}

tasks.named<JavaCompile>("compileTestJava") {
    options.encoding = "UTF-8"
    options.headerOutputDirectory.set(layout.buildDirectory.dir("generated/sources/headers/java/test"))
}

tasks.named<Jar>("jar") {
    archiveFileName.set("${project.name}.jar")
    from(rootDir) { include("plugin.json") }
}
fun ShadowJar.applyCommonSettings() {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    mergeServiceFiles()
    isReproducibleFileOrder = true
    isPreserveFileTimestamps = false
    from(rootDir) { include("plugin.json") }

    configurations = listOf(project.configurations.runtimeClasspath.get())
    from(project.sourceSets.main.get().output)
}
tasks.named<ShadowJar>("shadowJar") {
    applyCommonSettings()
    archiveFileName.set("${project.name}.jar")
}
fun registerNativeJar(variant: String) {
    tasks.register<ShadowJar>("jar$variant") {
        group = "build"
        description = "Assembles shadow JAR with $variant native library."
        archiveClassifier.set(variant.lowercase(Locale.getDefault()))
        applyCommonSettings()
        val nativeTask = project(":native").tasks.named<LinkSharedLibrary>("link$variant")
        dependsOn(nativeTask)
        from(nativeTask.map { it.linkedFile })
    }
}
registerNativeJar("Release")
registerNativeJar("Debug")
tasks.named("build") {
    dependsOn("jarRelease")
}
