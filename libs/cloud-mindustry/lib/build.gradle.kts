import org.gradle.api.credentials.PasswordCredentials
import org.gradle.authentication.http.BasicAuthentication

plugins {
    `java-library`
    `maven-publish`
}

group = "org.xcore"

val xcoreSnapshotsRepositoryUrl = providers.gradleProperty("xcoreMavenSnapshotsUrl")
    .orElse("https://maven.x-core.org/snapshots")
val xcoreReleasesRepositoryUrl = providers.gradleProperty("xcoreMavenReleasesUrl")
    .orElse("https://maven.x-core.org/releases")
val isSnapshotVersion = version.toString().endsWith("-SNAPSHOT")

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
    withSourcesJar()
}

repositories {
    mavenCentral()
    maven("https://raw.githubusercontent.com/Zelaux/MindustryRepo/master/repository")
}

dependencies {
    api(libs.cloud.core)
    api(libs.cloud.annotations)

    compileOnly(libs.mindustry.core)
    compileOnly(libs.arc.core)

    testImplementation(libs.junit.jupiter)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.named<Test>("test") {
    useJUnitPlatform()
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
        create<MavenPublication>("maven") {
            from(components["java"])

            groupId = "org.xcore"
            artifactId = "cloud-mindustry"
            version = version
        }
    }
}

tasks.register("getProjectVersion") {
    doLast {
        println(project.version.toString())
    }
}
