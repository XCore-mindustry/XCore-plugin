import com.xpdustry.toxopid.spec.ModMetadata
import com.xpdustry.toxopid.spec.ModPlatform
import com.xpdustry.toxopid.task.MindustryExec
import com.xpdustry.toxopid.Toxopid
import com.xpdustry.toxopid.extension.anukeJitpack
import com.xpdustry.toxopid.extension.anukeZelaux
import com.xpdustry.toxopid.extension.configureServer
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion

plugins {
    java
    `maven-publish`
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.shadow)
    alias(libs.plugins.toxopid)
}

// x-core.fun is expired so using GitHub provided domain for now
group = "io.github.xcore"
version = "3.0.0"

val metadata = ModMetadata(
    name = "xcore-plugin",
    displayName = "XCore-plugin",
    description = "The main plugin for XCore servers.",
    author = "osp54, OSPx#7122",
    version = project.version.toString(),
    minGameVersion = "146",
    mainClass = "${project.group}.XcorePlugin"
)

toxopid {
    compileVersion = "v${metadata.minGameVersion}"
    platforms = setOf(ModPlatform.SERVER)
}

publishing {
    repositories {
        maven {
            name = "xcoreRepository"
            url = uri("https://n1.x-core.fun/maven/private")
            credentials(PasswordCredentials::class)
            authentication {
                create<BasicAuthentication>("basic")
            }
        }
    }
    publications {
        create<MavenPublication>("maven") {
            groupId = "io.github.xcore"
            artifactId = "plugin"
            version = version
            from(components["java"])
        }
    }
}

repositories {
    mavenCentral()
    anukeZelaux()
    anukeJitpack()
    // Repository is down
    // maven("https://n1.x-core.fun/maven/releases")
    maven("https://maven.xpdustry.com/mindustry")
    maven("https://oss.sonatype.org/content/repositories/snapshots")
    maven("https://www.jitpack.io")
}

dependencies {
    compileOnly(toxopid.dependencies.mindustryCore)
    compileOnly(toxopid.dependencies.mindustryHeadless)
    compileOnly(toxopid.dependencies.arcCore)

    implementation(libs.fluent.base)
    implementation(libs.fluent.functions.cldr)
    implementation(libs.fluent.functions.icu)
    implementation(files("libs/flubundle-1.2.jar"))

    implementation(libs.sock)

    implementation("org.mongodb:mongodb-driver-sync:4.9.0")
    implementation("com.google.code.gson:gson:2.10.1")

    implementation("org.mindrot:jbcrypt:0.4")

    implementation("com.discord4j:discord4j-core:3.3.0-SNAPSHOT")
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
        temp.writeText(ModMetadata.toJson(metadata))
        from(temp)
    }

    from(configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) })
    duplicatesStrategy = DuplicatesStrategy.INCLUDE
}

tasks.withType<JavaCompile> {
    sourceCompatibility = "17"
    targetCompatibility = "17"
    options.encoding = "UTF-8"
}

kotlin {
    jvmToolchain(17)
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
        apiVersion = KotlinVersion.KOTLIN_2_1
    }
}

// Required for the GitHub actions
tasks.register("getProjectVersion") {
    doLast { println(project.version.toString()) }
}

tasks.register("runMainServer", MindustryExec::class.java) {
    group = Toxopid.TASK_GROUP_NAME
    configureServer()
    mods.setFrom(setOf(tasks.jar))
}

tasks.register("runServer", MindustryExec::class.java) {
    group = Toxopid.TASK_GROUP_NAME
    configureServer()
    mods.setFrom(setOf(tasks.jar))
}
