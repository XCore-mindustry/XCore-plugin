import org.gradle.internal.os.OperatingSystem
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.nativeplatform.tasks.LinkSharedLibrary
import org.gradle.jvm.toolchain.JavaToolchainService

plugins {
    `cpp-library`
    `cpp-unit-test`
    `java-base`
}

library {
    baseName.set("TileLogger")
    linkage.set(listOf(Linkage.SHARED))
}

val compileJava = project(":").tasks.named<JavaCompile>("compileJava")
val jniHeadersDir = compileJava.flatMap { it.options.headerOutputDirectory }

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
    dependsOn(compileJava)

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
        compilerArgs.addAll(listOf("/std:c++20", "/W4", "/EHsc"))
    } else {
        compilerArgs.addAll(listOf("-std=c++20", "-Wall", "-Wextra", "-pthread"))
    }
}

tasks.withType<LinkSharedLibrary>().configureEach {
    val os = OperatingSystem.current()

    if (os.isWindows) {
        linkerArgs.add("/DEBUG")
    }
}

// Native tests use the same headers and Gradle-selected platform toolchain.
unitTest {
    source.from("src/test/cpp")
    privateHeaders.from("src/main/headers")
}
val sanitizers = providers.gradleProperty("nativeSanitizers").map(String::toBoolean).orElse(false)
tasks.withType<CppCompile>().configureEach {
    if (name.contains("Release")) compilerArgs.add(if (OperatingSystem.current().isWindows) "/DNDEBUG" else "-DNDEBUG")
    if (sanitizers.get() && !OperatingSystem.current().isWindows) {
        compilerArgs.addAll(listOf("-fsanitize=address,undefined", "-fno-omit-frame-pointer"))
    }
}
tasks.withType<org.gradle.nativeplatform.tasks.AbstractLinkTask>().configureEach {
    if (!OperatingSystem.current().isWindows) {
        linkerArgs.add("-pthread")
        if (sanitizers.get()) linkerArgs.add("-fsanitize=address,undefined")
    }
}
