import com.xpdustry.toxopid.Toxopid
import com.xpdustry.toxopid.extension.anukeXpdustry
import com.xpdustry.toxopid.task.MindustryExec
import com.xpdustry.toxopid.spec.ModMetadata
import com.xpdustry.toxopid.spec.ModPlatform
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.gradle.api.tasks.testing.Test

plugins {
    java
    alias(libs.plugins.toxopid)
    alias(libs.plugins.shadow)
}

group = "org.xcore.plugin"
version = "3.1.1"
val mindustryVersion = libs.versions.mindustry.get()

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

val metadata = ModMetadata(
    name = "xcore-plugin",
    displayName = "XCore-plugin",
    description = "The main plugin for XCore servers.",
    author = "osp54, Radomyr (site: radomyr.net, github: BRamil0)",
    version = project.version.toString(),
    minGameVersion = mindustryVersion,
    mainClass = "${project.group}.XcorePlugin"
)

repositories {
    mavenCentral()
    anukeXpdustry()
    maven(url = "https://oss.sonatype.org/content/repositories/snapshots")
    maven(url = "https://www.jitpack.io")
}

dependencies {
    compileOnly(toxopid.dependencies.mindustryCore)
    compileOnly(toxopid.dependencies.arcCore)
    compileOnly(toxopid.dependencies.mindustryHeadless)
    implementation(project(":flubundle"))
    implementation(libs.cloud.mindustry)
    implementation(libs.mongodb.sync)
    implementation(libs.gson)
    implementation(libs.jbcrypt)
    implementation(libs.lettuce)
    implementation(variantOf(libs.netty.epoll) { classifier("linux-x86_64") })
    implementation(libs.bundles.jline)
    implementation(libs.avaje.inject)
    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)
    annotationProcessor(libs.avaje.inject.generator)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.mockito.core)
    testImplementation(libs.mockito.junit.jupiter)
    testImplementation(libs.assertj.core)
    testImplementation(libs.testcontainers.junit.jupiter)
    testImplementation(libs.testcontainers)
    testImplementation(libs.avaje.inject.test)
    testImplementation(toxopid.dependencies.arcCore)
    testImplementation(toxopid.dependencies.mindustryCore)
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
    from(generateModInfo)
}

tasks.assemble {
    dependsOn(tasks.shadowJar)
}

fun ShadowJar.applyCommonSettings() {
    archiveBaseName.set(project.name)
    archiveVersion.set(project.version.toString())
    archiveClassifier.set("")
    destinationDirectory.set(layout.buildDirectory.dir("libs"))
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    from(generateModInfo)
    mergeServiceFiles()
    isReproducibleFileOrder = true
    isPreserveFileTimestamps = false
}

tasks.named<ShadowJar>("shadowJar") {
    applyCommonSettings()
}

tasks.register<ShadowJar>("shadowJarRelease") {
    applyCommonSettings()
    archiveClassifier.set("release")
}

tasks.register("printArtifacts") {
    dependsOn(tasks.shadowJar, tasks.named("shadowJarRelease"))

    doLast {
        val shadow = tasks.named<ShadowJar>("shadowJar").get().archiveFile.get().asFile
        val release = tasks.named<ShadowJar>("shadowJarRelease").get().archiveFile.get().asFile

        println("shadowJar: ${shadow.absolutePath}")
        println("shadowJarRelease: ${release.absolutePath}")
    }
}

tasks.register("getProjectVersion") {
    doLast { println(project.version.toString()) }
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
}
