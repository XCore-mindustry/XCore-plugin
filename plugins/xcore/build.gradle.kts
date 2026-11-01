import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import com.xpdustry.toxopid.Toxopid
import com.xpdustry.toxopid.extension.anukeXpdustry
import com.xpdustry.toxopid.spec.ModMetadata
import com.xpdustry.toxopid.spec.ModPlatform
import com.xpdustry.toxopid.task.MindustryExec
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.tasks.testing.Test

plugins {
    java
    id("xcore.publish")
    alias(libs.plugins.toxopid)
    alias(libs.plugins.shadow)
}

group = "org.xcore.plugin"
version = "5.3.0"
val mindustryVersion = libs.versions.mindustry.get().removePrefix("v")

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
}

toxopid {
    compileVersion.set("v$mindustryVersion")
    runtimeVersion.set("v$mindustryVersion")
    platforms = setOf(ModPlatform.SERVER)
}

val metadata =
    ModMetadata(
        name = "xcore-plugin",
        displayName = "XCore-plugin",
        description = "The main plugin for XCore servers.",
        author = "osp54, Radomyr (site: radomyr.net, github: BRamil0)",
        version = project.version.toString(),
        minGameVersion = mindustryVersion,
        mainClass = "${project.group}.XcorePlugin",
    )

repositories {
    maven { url = uri("https://maven.x-core.org/releases") }
    mavenCentral()
    anukeXpdustry()
    maven(url = "https://oss.sonatype.org/content/repositories/snapshots")
    maven(url = "https://www.jitpack.io")
}

dependencies {
    compileOnly(toxopid.dependencies.mindustryCore)
    compileOnly(toxopid.dependencies.arcCore)
    compileOnly(toxopid.dependencies.mindustryHeadless)
    implementation(project(":protocol"))
    implementation(project(":libs:flubundle"))
    implementation(project(":libs:xcore-ui"))
    implementation(project(":libs:cloud-mindustry:lib"))
    implementation(libs.mongodb.sync)
    implementation(libs.mongodb.reactive)
    implementation(libs.gson)
    implementation(libs.jackson.dataformat.toml)
    implementation(libs.jbcrypt)
    implementation(platform(libs.netty.bom))
    implementation(libs.lettuce)
    implementation(variantOf(libs.netty.epoll) { classifier("linux-x86_64") })
    implementation(libs.avaje.inject)
    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)
    annotationProcessor(libs.avaje.inject.generator)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.mockito.core)
    testImplementation(libs.mockito.junit.jupiter)
    testImplementation(libs.assertj.core)
    testImplementation(project(":libs:mindustry-testkit:ui"))
    testImplementation(project(":libs:mindustry-testkit:fixtures"))
    testImplementation(libs.testcontainers.junit.jupiter)
    testImplementation(libs.testcontainers)
    testImplementation(libs.avaje.inject.test)
    testImplementation(toxopid.dependencies.arcCore)
    testImplementation(toxopid.dependencies.mindustryCore)
    testImplementation(toxopid.dependencies.mindustryHeadless)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testAnnotationProcessor(libs.avaje.inject.generator)
}

val generateModInfo by tasks.registering {
    val modFile = temporaryDir.resolve("plugin.json")
    inputs.property("metadata", ModMetadata.toJson(metadata, true))
    outputs.file(modFile)

    doLast {
        modFile.writeText(ModMetadata.toJson(metadata, true))
    }
}

tasks.jar {
    archiveBaseName.set("XCore-plugin")
    from(generateModInfo)
}

tasks.assemble {
    dependsOn(tasks.shadowJar)
}

fun ShadowJar.applyCommonSettings() {
    archiveBaseName.set("XCore-plugin")
    archiveVersion.set(project.version.toString())
    archiveClassifier.set("")
    destinationDirectory.set(layout.buildDirectory.dir("libs"))
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    from(generateModInfo)
    mergeServiceFiles()
    filesMatching("META-INF/services/**") {
        duplicatesStrategy = DuplicatesStrategy.INCLUDE
    }
    isReproducibleFileOrder = true
    isPreserveFileTimestamps = false
}

tasks.named<ShadowJar>("shadowJar") {
    applyCommonSettings()
}

tasks.register<ShadowJar>("shadowJarRelease") {
    applyCommonSettings()
    destinationDirectory.set(layout.buildDirectory.dir("libs/release"))
    from(sourceSets.main.get().output)
    configurations = listOf(project.configurations.runtimeClasspath.get())
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            groupId = project.group.toString()
            artifactId = "XCore-plugin"
            version = project.version.toString()
            artifact(tasks.named("shadowJarRelease"))

            pom {
                name.set("XCore-plugin")
                description.set("The main plugin for XCore servers.")
            }
        }
    }
}

tasks.register("printArtifacts") {
    dependsOn(tasks.shadowJar, tasks.named("shadowJarRelease"))

    doLast {
        val shadow =
            tasks
                .named<ShadowJar>("shadowJar")
                .get()
                .archiveFile
                .get()
                .asFile
        val release =
            tasks
                .named<ShadowJar>("shadowJarRelease")
                .get()
                .archiveFile
                .get()
                .asFile

        println("shadowJar: ${shadow.absolutePath}")
        println("shadowJarRelease: ${release.absolutePath}")
    }
}

tasks.register("validateCi") {
    dependsOn(tasks.test, tasks.shadowJar)
}

tasks.register("validateRelease") {
    dependsOn(tasks.test, tasks.named("shadowJarRelease"))
}

tasks.withType<MindustryExec> {
    group = Toxopid.TASK_GROUP_NAME
    classpath(tasks.downloadMindustryServer)
    mainClass.set("mindustry.server.ServerLauncher")
    modsDirPath.convention("./config/mods")
    standardInput = System.`in`
    mods.setFrom(tasks.shadowJar.map { it.archiveFile })
}

tasks.register("runMainServer", MindustryExec::class)
tasks.register("runServer", MindustryExec::class)

tasks.named<MindustryExec>("runMainServer") {
    workingDir = file("./server/runMainServer")
    doFirst {
        workingDir.mkdirs()
    }
}

tasks.named<MindustryExec>("runServer") {
    workingDir = file("./server/runServer")
    doFirst {
        workingDir.mkdirs()
    }
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()

    // GameThread reports violations by default so a production server keeps serving.
    // Tests run it strict, so a thread-affinity regression fails the build instead of
    // reaching a deployment. Overridable with -PxcoreStrictThreads=false.
    val strictThreads = providers.gradleProperty("xcoreStrictThreads").orElse("true").get()
    systemProperty("xcore.strictThreads", strictThreads)
}

tasks.test {
    useJUnitPlatform { excludeTags("ui-render") }
}

tasks.register<Test>("exportUiFixtures") {
    description = "Export localized Settings and Top packets for the real-client renderer."
    group = "verification"
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    useJUnitPlatform { includeTags("ui-render") }
    val output = layout.buildDirectory.dir("ui-render/packets")
    systemProperty("xcore.uiRenderOutput", output.get().asFile.absolutePath)
    outputs.dir(output)
    // A removed locale or fixture must not leave a stale packet in the render matrix.
    doFirst { delete(output) }
}
