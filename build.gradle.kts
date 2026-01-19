import com.xpdustry.toxopid.Toxopid
import com.xpdustry.toxopid.extension.anukeXpdustry
import com.xpdustry.toxopid.task.MindustryExec
import com.xpdustry.toxopid.spec.ModMetadata
import com.xpdustry.toxopid.spec.ModPlatform

plugins {
    java
    id("com.xpdustry.toxopid") version "4.1.2"
    id("com.gradleup.shadow") version "8.3.5"
}

group = "org.xcore.plugin"
version = "2.9.0"
val mindustryVersion = "154.3"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
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
    author = "osp54, ",
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

    implementation("com.github.osp54:Sock:9d465f7")
    implementation("org.mongodb:mongodb-driver-sync:5.6.2")
    implementation("com.google.code.gson:gson:2.10.1")
    implementation("org.mindrot:jbcrypt:0.4")
    implementation("com.discord4j:discord4j-core:3.3.0")

    implementation("io.netty:netty-transport-native-epoll:4.1.107.Final:linux-x86_64")

    implementation("org.jline:jline-terminal-jna:3.30.6")

    implementation("org.jline:jline-reader:3.30.6")
    implementation("org.jline:jline-console:3.30.6")

    compileOnly("org.projectlombok:lombok:1.18.30")
    annotationProcessor("org.projectlombok:lombok:1.18.30")

}

val generateModInfo by tasks.registering {
    val modFile = temporaryDir.resolve("plugin.json")
    inputs.property("metadata", ModMetadata.toJson(metadata, true))
    outputs.file(modFile)

    doLast {
        modFile.writeText(ModMetadata.toJson(metadata, true))
    }
}

tasks.shadowJar {
    archiveClassifier.set("")

    from(generateModInfo)

    mergeServiceFiles()

    minimize {
        exclude(dependency("com.discord4j:.*:.*"))
        exclude(dependency("org.jline:.*:.*"))
    }

    val shadowPrefix = "org.xcore.plugin.shadow"

    relocate("com.google.gson", "$shadowPrefix.gson")
    relocate("com.mongodb", "$shadowPrefix.mongo")
    relocate("org.bson", "$shadowPrefix.bson")
    relocate("org.mindrot.jbcrypt", "$shadowPrefix.jbcrypt")

    relocate("discord4j", "$shadowPrefix.discord4j")
    relocate("reactor", "$shadowPrefix.reactor")
    relocate("io.netty", "$shadowPrefix.netty")

    relocate("com.ospx.sock", "$shadowPrefix.sock")

    // relocate("org.jline", "$shadowPrefix.jline") fucking jline

    isReproducibleFileOrder = true
    isPreserveFileTimestamps = false
}

tasks.jar {
    enabled = false
}

tasks.assemble {
    dependsOn(tasks.shadowJar)
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