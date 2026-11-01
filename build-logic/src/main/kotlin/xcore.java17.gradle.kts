// Libraries that AdminTools (a Java 17 client mod, also dexed for Android) consumes.
// Built with the same JDK 25 as everything else, but targeting the Java 17 API and class format.
plugins {
    java
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

tasks.named<JavaCompile>("compileJava") {
    options.release = 17
}

val checkJava17Classes by tasks.registering {
    description = "Fails if any main class file is newer than Java 17 (major version 61)."
    val classes = sourceSets.main.map { it.output.classesDirs }
    inputs.files(classes)
    doLast {
        val newer =
            classes.get().asFileTree.matching { include("**/*.class") }.filter { file ->
                file.inputStream().use { it.readNBytes(8) }.let { (it[6].toInt() and 0xff shl 8) or (it[7].toInt() and 0xff) } > 61
            }
        check(newer.isEmpty) { "Classes newer than Java 17: ${newer.files.take(5)}" }
    }
}

tasks.named("check") {
    dependsOn(checkJava17Classes)
}
