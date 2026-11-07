plugins {
    base
    id("xcore.publish") apply false
}

version = "0.3.0"
val junitBom = libs.junit.bom

subprojects {
    apply(plugin = "java-library")
    apply(plugin = "xcore.publish")

    group = "org.xcore.testkit"
    version = parent!!.version

    repositories {
        mavenCentral()
        maven("https://maven.xpdustry.com/mindustry")
        maven("https://raw.githubusercontent.com/Zelaux/MindustryRepo/master/repository")
    }

    extensions.configure<JavaPluginExtension> {
        toolchain.languageVersion.set(JavaLanguageVersion.of(25))
        withSourcesJar()
    }

    dependencies {
        "testImplementation"(platform(junitBom))
        "testImplementation"("org.junit.jupiter:junit-jupiter")
        "testRuntimeOnly"("org.junit.platform:junit-platform-launcher")
    }

    tasks.withType<Test>().configureEach { useJUnitPlatform() }

    extensions.configure<PublishingExtension> {
        publications {
            create<MavenPublication>("maven") {
                groupId = project.group.toString()
                artifactId = project.name
                version = project.version.toString()
                from(components["java"])

                pom {
                    name.set("mindustry-testkit-${project.name}")
                    description.set("Deterministic testing toolkit for Mindustry plugins: ${project.name} module.")
                    url.set("https://github.com/XCore-mindustry/xcore/tree/main/libs/mindustry-testkit")
                    licenses {
                        license {
                            name.set("Mozilla Public License 2.0")
                            url.set("https://www.mozilla.org/MPL/2.0/")
                        }
                    }
                    developers {
                        developer {
                            id.set("XCore-mindustry")
                            name.set("XCore Mindustry")
                        }
                    }
                    scm {
                        connection.set("scm:git:git://github.com/XCore-mindustry/xcore.git")
                        developerConnection.set("scm:git:ssh://git@github.com/XCore-mindustry/xcore.git")
                        url.set("https://github.com/XCore-mindustry/xcore")
                    }
                }
            }
        }
    }
}
