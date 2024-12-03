import com.xpdustry.toxopid.extension.anukeZelaux
import com.xpdustry.toxopid.spec.ModMetadata
import com.xpdustry.toxopid.spec.ModPlatform
import com.xpdustry.toxopid.task.GithubAssetDownload

plugins {
    `java-library`
    id("com.gradleup.shadow") version "8.3.5"
    id("com.xpdustry.toxopid") version "4.1.1"
}

val metadata = ModMetadata.fromJson(file("plugin.json"))
metadata.version = project.version.toString()

toxopid {
    compileVersion = "v${metadata.minGameVersion}"
    platforms = setOf(ModPlatform.SERVER)
}

repositories {
    anukeZelaux()
}

dependencies {
    compileOnly(toxopid.dependencies.mindustryCore)
    compileOnly(toxopid.dependencies.mindustryHeadless)
    compileOnly(toxopid.dependencies.arcCore)

    implementation(libs.fluent.base)
    implementation(libs.fluent.functions.cldr)
    implementation(libs.fluent.functions.icu)
    implementation(rootProject.files("libs/flubundle-1.2.jar"))

    implementation(libs.sock)

    implementation("org.mongodb:mongodb-driver-sync:4.9.0")
    implementation("com.google.code.gson:gson:2.10.1")

    // Database
    implementation(libs.mongodb.driver)
    implementation(libs.bson.kotlinx)
    implementation(libs.nitrite.potassium)
    implementation(libs.nitrite.mvstore)

    implementation("org.mindrot:jbcrypt:0.4")

    implementation("com.discord4j:discord4j-core:3.3.0-SNAPSHOT")
    implementation("io.netty:netty-transport-native-epoll:4.1.89.Final:linux-aarch_64")

    implementation("org.jline:jline-terminal-jna:3.21.0")
    implementation("org.jline:jline-reader:3.21.0")
    implementation("org.jline:jline-console:3.21.0")

    compileOnly("org.projectlombok:lombok:1.18.26")
    annotationProcessor("org.projectlombok:lombok:1.18.26")
}

configurations.runtimeClasspath {
    exclude("org.jetbrains.kotlin")
    exclude("org.jetbrains.kotlinx")
}

val generateMetadataFile by tasks.registering {
    inputs.property("metadata", metadata)
    val output = temporaryDir.resolve("plugin.json")
    outputs.file(output)
    doLast { output.writeText(ModMetadata.toJson(metadata)) }
}

tasks.shadowJar {
    archiveFileName = "${metadata.name}.jar"
    archiveClassifier = "plugin"
    from(generateMetadataFile)
    from(rootProject.file("LICENSE.md")) { into("META-INF") }
    minimize()
}

val downloadKotlinRuntime by tasks.registering(GithubAssetDownload::class) {
    owner.set("xpdustry")
    repo.set("kotlin-runtime")
    asset.set("kotlin-runtime.jar")
    version.set("v3.2.0-k.1.9.23")
}

tasks.runMindustryServer {
    mods.from(downloadKotlinRuntime)
}
