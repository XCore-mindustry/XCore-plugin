plugins {
    `java-library`
    `maven-publish`
}

group = "org.xcore"
val baseVersion = "0.1.0-SNAPSHOT"
version = findProperty("xcorePublishVersion") as? String ?: baseVersion

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
    withSourcesJar()
}

repositories {
    mavenLocal()
    mavenCentral()
    maven("https://maven.x-core.org/releases")
    maven("https://maven.x-core.org/snapshots")
    maven("https://maven.xpdustry.com/mindustry")
    maven("https://raw.githubusercontent.com/Zelaux/MindustryRepo/master/repository")
    maven("https://www.jitpack.io")
}

val xcoreSnapshotsRepositoryUrl = findProperty("xcoreMavenSnapshotsUrl") as? String ?: "https://maven.x-core.org/snapshots"
val xcoreReleasesRepositoryUrl = findProperty("xcoreMavenReleasesUrl") as? String ?: "https://maven.x-core.org/releases"

publishing {
    repositories {
        maven {
            name = "xcoreRepositorySnapshots"
            url = uri(xcoreSnapshotsRepositoryUrl)
            credentials {
                username = findProperty("xcoreMavenUsername") as? String ?: ""
                password = findProperty("xcoreMavenPassword") as? String ?: ""
            }
            authentication {
                create<BasicAuthentication>("basic")
            }
        }
        maven {
            name = "xcoreRepositoryReleases"
            url = uri(xcoreReleasesRepositoryUrl)
            credentials {
                username = findProperty("xcoreMavenUsername") as? String ?: ""
                password = findProperty("xcoreMavenPassword") as? String ?: ""
            }
            authentication {
                create<BasicAuthentication>("basic")
            }
        }
    }
    publications {
        create<MavenPublication>("maven") {
            groupId = "org.xcore"
            artifactId = "xcore-ui"
            version = project.version.toString()
            from(components["java"])
        }
    }
}

dependencies {
    compileOnly(libs.mindustry.core)
    compileOnly(libs.arc.core)
    compileOnly(libs.flubundle)

    testImplementation(libs.mindustry.core)
    testImplementation(libs.arc.core)
    testImplementation(libs.flubundle)
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}
