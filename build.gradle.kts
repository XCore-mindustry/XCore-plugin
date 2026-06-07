import com.xpdustry.toxopid.Toxopid
import com.xpdustry.toxopid.extension.anukeXpdustry
import com.xpdustry.toxopid.task.MindustryExec
import com.xpdustry.toxopid.spec.ModMetadata
import com.xpdustry.toxopid.spec.ModPlatform
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.gradle.api.tasks.testing.Test
import org.gradle.api.credentials.PasswordCredentials
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.authentication.http.BasicAuthentication

plugins {
    java
    `maven-publish`
    alias(libs.plugins.toxopid)
    alias(libs.plugins.shadow)
}

group = "org.xcore.plugin"
val baseVersion = "4.2.1"
version = providers.gradleProperty("xcorePublishVersion").orElse(baseVersion).get()
val isSnapshotVersion = version.toString().endsWith("-SNAPSHOT")
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

val xcoreSnapshotsRepositoryUrl = providers.gradleProperty("xcoreMavenSnapshotsUrl")
    .orElse("https://maven.x-core.org/snapshots")
val xcoreReleasesRepositoryUrl = providers.gradleProperty("xcoreMavenReleasesUrl")
    .orElse("https://maven.x-core.org/releases")

repositories {
    maven { url = uri("https://maven.x-core.org/snapshots") }
    maven { url = uri("https://maven.x-core.org/releases") }
    mavenCentral()
    anukeXpdustry()
    maven(url = "https://oss.sonatype.org/content/repositories/snapshots")
    maven(url = "https://www.jitpack.io")
    maven(url = xcoreReleasesRepositoryUrl)
    maven(url = xcoreSnapshotsRepositoryUrl)
}

dependencies {
    compileOnly(toxopid.dependencies.mindustryCore)
    compileOnly(toxopid.dependencies.arcCore)
    compileOnly(toxopid.dependencies.mindustryHeadless)
    implementation(libs.xcore.protocol.java)
    implementation(libs.flubundle)
    implementation(libs.cloud.mindustry)
    implementation(libs.mongodb.sync)
    implementation(libs.gson)
    implementation(libs.jackson.dataformat.toml)
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
    destinationDirectory.set(layout.buildDirectory.dir("libs/release"))
    from(sourceSets.main.get().output)
    configurations = listOf(project.configurations.runtimeClasspath.get())
}

val publishJarTask = if (isSnapshotVersion) {
    tasks.named<ShadowJar>("shadowJar")
} else {
    tasks.named<ShadowJar>("shadowJarRelease")
}

publishing {
    repositories {
        maven {
            name = "xcoreRepositorySnapshots"
            url = uri(xcoreSnapshotsRepositoryUrl.get())
            credentials(PasswordCredentials::class)
            authentication {
                create<BasicAuthentication>("basic")
            }
        }

        maven {
            name = "xcoreRepositoryReleases"
            url = uri(xcoreReleasesRepositoryUrl.get())
            credentials(PasswordCredentials::class)
            authentication {
                create<BasicAuthentication>("basic")
            }
        }
    }

    publications {
        create<MavenPublication>("maven") {
            groupId = project.group.toString()
            artifactId = project.name
            version = project.version.toString()
            artifact(publishJarTask)

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
        val shadow = tasks.named<ShadowJar>("shadowJar").get().archiveFile.get().asFile
        val release = tasks.named<ShadowJar>("shadowJarRelease").get().archiveFile.get().asFile

        println("shadowJar: ${shadow.absolutePath}")
        println("shadowJarRelease: ${release.absolutePath}")
    }
}

tasks.register("getProjectVersion") {
    doLast { println(project.version.toString()) }
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
}
