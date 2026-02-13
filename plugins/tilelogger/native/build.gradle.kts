import org.gradle.internal.os.OperatingSystem
import org.gradle.nativeplatform.tasks.LinkSharedLibrary
import org.gradle.jvm.toolchain.JavaToolchainService

plugins {
    `cpp-library`
    `java-base`
}

library {
    baseName.set("TileLogger")
    linkage.set(listOf(Linkage.SHARED))
}

val jniHeadersDir = project(":").layout.buildDirectory.dir("generated/sources/headers/java/main")

library {
    source.from("src/main/cpp")
    publicHeaders.from("src/main/headers", jniHeadersDir)
}

val javaToolchains = extensions.getByType<JavaToolchainService>()
val javaLauncher = javaToolchains.launcherFor {
    languageVersion.set(JavaLanguageVersion.of(libs.versions.java.get().toInt()))
}
val javaHomeProvider = javaLauncher.map { it.metadata.installationPath.asFile }

tasks.withType<CppCompile>().configureEach {
    dependsOn(":compileJava")

    includes.from(javaHomeProvider.map { it.resolve("include") })

    val os = OperatingSystem.current()
    includes.from(javaHomeProvider.map {
        when {
            os.isLinux -> it.resolve("include/linux")
            os.isWindows -> it.resolve("include/win32")
            os.isMacOsX -> it.resolve("include/darwin")
            else -> it
        }
    })

    if (os.isWindows) {
        compilerArgs.addAll(listOf("/std:c++20", "/O2", "/DNDEBUG", "/W3", "/EHsc"))
    } else {
        compilerArgs.addAll(listOf("-std=c++20", "-O3", "-DNDEBUG", "-s", "-Wall"))
    }
}

tasks.withType<LinkSharedLibrary>().configureEach {
    val os = OperatingSystem.current()

    if (os.isWindows) {
        linkerArgs.add("/DEBUG")
    }
}