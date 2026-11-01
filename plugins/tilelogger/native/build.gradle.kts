import org.gradle.internal.os.OperatingSystem
import org.gradle.nativeplatform.tasks.LinkSharedLibrary

plugins {
    `cpp-library`
    `cpp-unit-test`
}

library {
    baseName.set("TileLogger")
    linkage.set(listOf(Linkage.SHARED))
}

library {
    source.from("src/main/cpp")
    publicHeaders.from("src/main/headers")
}

tasks.withType<CppCompile>().configureEach {
    val os = OperatingSystem.current()
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
