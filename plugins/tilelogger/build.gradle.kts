import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import com.xpdustry.toxopid.Toxopid
import com.xpdustry.toxopid.extension.anukeXpdustry
import com.xpdustry.toxopid.spec.ModPlatform
import org.gradle.nativeplatform.tasks.LinkSharedLibrary
import java.util.Locale

plugins {
    java
    alias(libs.plugins.avaje.inject)
    alias(libs.plugins.shadow)
    alias(libs.plugins.toxopid)
    alias(libs.plugins.jmh)
}

group = "org.xcore.plugin"
version = groovy.json.JsonSlurper().parse(file("plugin.json")).let { (it as Map<*, *>)["version"].toString() }

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(25))
}

toxopid {
    compileVersion.set(libs.versions.mindustry.get())
    runtimeVersion.set(libs.versions.mindustry.get())
    platforms = setOf(ModPlatform.SERVER)
}

repositories {
    anukeXpdustry()
    mavenCentral()
    maven("https://maven.x-core.org/releases")
}

dependencies {
    compileOnly(toxopid.dependencies.arcCore)
    compileOnly(toxopid.dependencies.mindustryCore)
    compileOnly(toxopid.dependencies.mindustryHeadless)

    compileOnly(project(":plugins:xcore"))
    compileOnly(project(":libs:flubundle"))
    compileOnly(project(":libs:cloud-mindustry:lib"))

    compileOnly(libs.avaje.inject)
    annotationProcessor(libs.avaje.inject.generator)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation(project(":libs:flubundle"))
    testImplementation(project(":plugins:xcore"))
    testImplementation(libs.avaje.inject)
    testImplementation(libs.assertj.core)
    testImplementation(libs.mockito.core)
    testImplementation(libs.mockito.junit.jupiter)
    testImplementation(project(":libs:mindustry-testkit:fixtures"))
    testImplementation(toxopid.dependencies.arcCore)
    testImplementation(toxopid.dependencies.mindustryCore)
    testImplementation(toxopid.dependencies.mindustryHeadless)
}

tasks.withType<Test> {
    useJUnitPlatform()
    jvmArgs("--enable-native-access=ALL-UNNAMED", "--illegal-native-access=deny")
}

// Java/native boundary benchmarks against the release native library: ./gradlew jmh
jmh {
    jmhVersion.set("1.37")
    duplicateClassesStrategy.set(DuplicatesStrategy.EXCLUDE)
    failOnError.set(true)
    includeTests.set(false)
    resultFormat.set("JSON")
}
dependencies {
    "jmhImplementation"(project(":plugins:xcore"))
    "jmhImplementation"(toxopid.dependencies.mindustryCore)
    "jmhImplementation"(toxopid.dependencies.arcCore)
}
tasks.named<ShadowJar>("jmhJar") {
    archiveFileName.set("TileLogger-jmh.jar")
    configurations = listOf(project.configurations.getByName("jmhRuntimeClasspath"))
    mergeServiceFiles()
    // The JMH plugin's legacy Shadow integration emits a thin class path on Shadow 9.
    manifest.attributes["Class-Path"] = ""
}

tasks.named<JavaCompile>("compileJava") {
    options.encoding = "UTF-8"
}

tasks.named<JavaCompile>("compileTestJava") {
    options.encoding = "UTF-8"
}

tasks.named<Jar>("jar") {
    archiveFileName.set("TileLogger.jar")
    from(projectDir) { include("plugin.json") }
}

fun ShadowJar.applyCommonSettings() {
    archiveBaseName.set("TileLogger")
    archiveVersion.set("")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    mergeServiceFiles()
    filesMatching("META-INF/services/**") {
        duplicatesStrategy = DuplicatesStrategy.INCLUDE
    }
    isReproducibleFileOrder = true
    isPreserveFileTimestamps = false
    from(projectDir) { include("plugin.json") }

    configurations = listOf(project.configurations.runtimeClasspath.get())
    from(
        project.sourceSets.main
            .get()
            .output,
    )
}
tasks.named<ShadowJar>("shadowJar") {
    applyCommonSettings()
    archiveFileName.set("TileLogger.jar")
}

fun registerNativeJar(variant: String) {
    tasks.register<ShadowJar>("jar$variant") {
        group = "build"
        description = "Assembles shadow JAR with $variant native library."
        archiveClassifier.set(variant.lowercase(Locale.getDefault()))
        applyCommonSettings()
        val nativeTask = project(":plugins:tilelogger:native").tasks.named<LinkSharedLibrary>("link$variant")
        dependsOn(nativeTask)
        from(nativeTask.map { it.linkedFile })
    }
}
registerNativeJar("Release")
registerNativeJar("Debug")
tasks.named("build") {
    dependsOn("jarRelease")
}

gradle.projectsEvaluated {
    tasks.named<Test>("test") {
        val linkDebug = project(":plugins:tilelogger:native").tasks.named<LinkSharedLibrary>("linkDebug")
        dependsOn(linkDebug)
        classpath +=
            files(
                linkDebug.map {
                    it.linkedFile
                        .get()
                        .asFile.parentFile
                },
            )
    }
    val linkRelease = project(":plugins:tilelogger:native").tasks.named<LinkSharedLibrary>("linkRelease")
    dependencies {
        "jmhRuntimeOnly"(files(linkRelease.map { it.linkedFile.get().asFile.parentFile }).builtBy(linkRelease))
    }
}

// Benchmarks are never run by check, but must keep compiling against the API.
tasks.named("check") { dependsOn(":plugins:tilelogger:native:check", "compileJmhJava") }

// This plugin ships through GitHub releases, not Maven.
tasks.register("getProjectVersion") {
    val releaseVersion = project.version.toString()
    doLast { println(releaseVersion) }
}
