import org.gradle.api.credentials.PasswordCredentials
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.authentication.http.BasicAuthentication

plugins {
    java
    `maven-publish`
}

fun pyprojectBaseVersion(): String {
    val versionLine = file("pyproject.toml").readLines()
        .firstOrNull { it.trim().startsWith("version = ") }
        ?: error("Unable to locate project version in pyproject.toml")

    return Regex("\"([^\"]+)\"")
        .find(versionLine)
        ?.groupValues
        ?.get(1)
        ?: error("Unable to parse project version from pyproject.toml")
}

group = "org.xcore"
val baseVersion = pyprojectBaseVersion()
version = providers.gradleProperty("xcorePublishVersion").orElse("$baseVersion-SNAPSHOT").get()

val xcoreSnapshotsRepositoryUrl = providers.gradleProperty("xcoreMavenSnapshotsUrl")
    .orElse("https://maven.x-core.org/snapshots")
val xcoreReleasesRepositoryUrl = providers.gradleProperty("xcoreMavenReleasesUrl")
    .orElse("https://maven.x-core.org/releases")

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
    withSourcesJar()
}

sourceSets {
    main {
        java.srcDir("java/core/src/main/java")
    }
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
        create<MavenPublication>("mavenJava") {
            from(components["java"])
            groupId = project.group.toString()
            artifactId = "xcore-protocol-java"
            version = project.version.toString()

            pom {
                name.set("xcore-protocol-java")
                description.set("Generated Java protocol artifacts for the canonical XCore protocol surface.")
            }
        }
    }
}

tasks.register("getProjectVersion") {
    doLast {
        println(project.version.toString())
    }
}

tasks.register("getBaseVersion") {
    doLast {
        println(baseVersion)
    }
}
