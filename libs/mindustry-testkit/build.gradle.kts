import org.gradle.api.credentials.PasswordCredentials
import org.gradle.authentication.http.BasicAuthentication

plugins {
    base
}

val baseVersion = "0.1.0"
version = providers.gradleProperty("xcorePublishVersion").orElse(baseVersion).get()

val xcoreSnapshotsRepositoryUrl =
    providers
        .gradleProperty("xcoreMavenSnapshotsUrl")
        .orElse("https://maven.x-core.org/snapshots")
val xcoreReleasesRepositoryUrl =
    providers
        .gradleProperty("xcoreMavenReleasesUrl")
        .orElse("https://maven.x-core.org/releases")

tasks.register("getProjectVersion") {
    doLast {
        println(project.version.toString())
    }
}

subprojects {
    apply(plugin = "java-library")
    apply(plugin = "maven-publish")

    group = "org.xcore.testkit"
    version = rootProject.version

    repositories {
        mavenLocal()
        mavenCentral()
        maven("https://maven.xpdustry.com/mindustry")
        maven("https://raw.githubusercontent.com/Zelaux/MindustryRepo/master/repository")
    }

    extensions.configure<JavaPluginExtension> {
        toolchain.languageVersion.set(JavaLanguageVersion.of(25))
        withSourcesJar()
    }

    dependencies {
        "testImplementation"(platform("org.junit:junit-bom:5.12.1"))
        "testImplementation"("org.junit.jupiter:junit-jupiter")
        "testRuntimeOnly"("org.junit.platform:junit-platform-launcher")
    }

    tasks.withType<Test>().configureEach { useJUnitPlatform() }

    extensions.configure<PublishingExtension> {
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
                    name.set("mindustry-testkit-${project.name}")
                    description.set("Deterministic testing toolkit for Mindustry plugins: ${project.name} module.")
                    url.set("https://github.com/XCore-mindustry/mindustry-testkit")
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
                        connection.set("scm:git:git://github.com/XCore-mindustry/mindustry-testkit.git")
                        developerConnection.set("scm:git:ssh://github.com:XCore-mindustry/mindustry-testkit.git")
                        url.set("https://github.com/XCore-mindustry/mindustry-testkit")
                    }
                }
            }
        }
    }
}
