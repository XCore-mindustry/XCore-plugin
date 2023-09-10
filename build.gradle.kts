
import fr.xpdustry.toxopid.Toxopid
import fr.xpdustry.toxopid.spec.ModMetadata
import fr.xpdustry.toxopid.spec.ModPlatform
import fr.xpdustry.toxopid.task.MindustryExec

plugins {
    java
    id("fr.xpdustry.toxopid") version "3.0.0"
} 

group = "org.xcore.plugin"
version = "2.6.6"
val mindustryVersion = "146"

toxopid {
    compileVersion.set("v$mindustryVersion")
    runtimeVersion.set("v$mindustryVersion")
    platforms.add(ModPlatform.HEADLESS)
}

val metadata = ModMetadata(
    name = "xcore-plugin",
    displayName = "XCore-plugin",
    description = "The main plugin for XCore servers.",
    author = "osp54, OSPx#7122",
    version = project.version.toString(),
    minGameVersion = mindustryVersion,
    main = "${project.group}.XcorePlugin"
)

repositories {
    mavenCentral()
    maven(url = "https://maven.xpdustry.com/snapshots")
    maven(url = "https://maven.xpdustry.com/mindustry")
    maven(url = "https://oss.sonatype.org/content/repositories/snapshots")
    maven(url = "https://www.jitpack.io")
}

dependencies {
    compileOnly("com.github.Anuken.arc:arc-core:v$mindustryVersion")
    compileOnly("com.github.Anuken.Mindustry:core:v$mindustryVersion")
    compileOnly("com.github.Anuken.Mindustry:server:v$mindustryVersion")

    implementation("com.github.xzxadixzx.useful-stuffs:bundle:bcf5708286")
    implementation("com.github.osp54:Sock:9d465f7")

    implementation("org.mongodb:mongodb-driver-sync:4.9.0")
    implementation("com.google.code.gson:gson:2.10.1")

    implementation("com.discord4j:discord4j-core:3.2.4")
    implementation("io.netty:netty-transport-native-epoll:4.1.89.Final:linux-aarch_64")

    implementation("org.jline:jline-terminal-jna:3.21.0")
    implementation("org.jline:jline-reader:3.21.0")
    implementation("org.jline:jline-console:3.21.0")

    compileOnly("org.projectlombok:lombok:1.18.26")
    annotationProcessor("org.projectlombok:lombok:1.18.26")
}

tasks.jar {
    doFirst {
        val temp = temporaryDir.resolve("plugin.json")
        temp.writeText(metadata.toJson(true))
        from(temp)
    }

    from(configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) })
    duplicatesStrategy = DuplicatesStrategy.INCLUDE
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}

// Required for the GitHub actions
tasks.register("getProjectVersion") {
    doLast { println(project.version.toString()) }
}

tasks.register("runMainServer", MindustryExec::class.java) {
    group = Toxopid.TASK_GROUP_NAME
    classpath(tasks.downloadMindustryServer)
    mainClass.convention("mindustry.server.ServerLauncher")
    modsPath.convention("./config/mods")
    standardInput = System.`in`
    mods.setFrom(setOf(tasks.jar))
}

tasks.register("runServer", MindustryExec::class.java) {
    group = Toxopid.TASK_GROUP_NAME
    classpath(tasks.downloadMindustryServer)
    mainClass.convention("mindustry.server.ServerLauncher")
    modsPath.convention("./config/mods")
    standardInput = System.`in`
    mods.setFrom(setOf(tasks.jar))
}
