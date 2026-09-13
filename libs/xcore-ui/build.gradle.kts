import org.gradle.api.credentials.PasswordCredentials
import org.gradle.authentication.http.BasicAuthentication

plugins {
    `java-library`
    `maven-publish`
}

group = "org.xcore"
val baseVersion = "0.1.0"
version = providers.gradleProperty("xcorePublishVersion").orElse(baseVersion).get()
val isSnapshotVersion = version.toString().endsWith("-SNAPSHOT")

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

val xcoreSnapshotsRepositoryUrl = providers.gradleProperty("xcoreMavenSnapshotsUrl")
    .orElse("https://maven.x-core.org/snapshots")
val xcoreReleasesRepositoryUrl = providers.gradleProperty("xcoreMavenReleasesUrl")
    .orElse("https://maven.x-core.org/releases")

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
        create<MavenPublication>("maven") {
            groupId = project.group.toString()
            artifactId = project.name
            version = project.version.toString()
            from(components["java"])

            pom {
                name.set("xcore-ui")
                description.set("Type-safe, reactive server-driven UI framework for Mindustry v160 servers.")
                url.set("https://github.com/XCore-mindustry/xcore-ui")
                licenses {
                    license {
                        name.set("MIT License")
                        url.set("https://opensource.org/licenses/MIT")
                    }
                }
                developers {
                    developer {
                        id.set("XCore-mindustry")
                        name.set("XCore Mindustry")
                    }
                }
                scm {
                    connection.set("scm:git:git://github.com/XCore-mindustry/xcore-ui.git")
                    developerConnection.set("scm:git:ssh://github.com:XCore-mindustry/xcore-ui.git")
                    url.set("https://github.com/XCore-mindustry/xcore-ui")
                }
            }
        }
    }
}

dependencies {
    compileOnly(libs.mindustry.core)
    compileOnly(libs.arc.core)

    testImplementation(libs.mindustry.core)
    testImplementation(libs.arc.core)
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}

tasks.register("getProjectVersion") {
    doLast {
        println(project.version.toString())
    }
}
