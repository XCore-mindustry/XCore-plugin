plugins {
    `java-library`
    id("xcore.publish")
}

group = "org.xcore"
version = "0.4.0"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
    withSourcesJar()
}

repositories {
    mavenCentral()
    maven("https://maven.xpdustry.com/mindustry")
    maven("https://raw.githubusercontent.com/Zelaux/MindustryRepo/master/repository")
    maven("https://www.jitpack.io")
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            groupId = project.group.toString()
            artifactId = "xcore-ui"
            version = project.version.toString()
            from(components["java"])

            pom {
                name.set("xcore-ui")
                description.set("Type-safe, reactive server-driven UI framework for Mindustry v160 servers.")
                url.set("https://github.com/XCore-mindustry/xcore/tree/main/libs/xcore-ui")
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

dependencies {
    compileOnly(libs.mindustry.core)
    compileOnly(libs.arc.core)

    testImplementation(libs.mindustry.core)
    testImplementation(libs.arc.core)
    testImplementation(project(":libs:mindustry-testkit:ui"))
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform { excludeTags("ui-render") }
}

tasks.register<Test>("exportUiFixtures") {
    description = "Write the menus whose real-client layout backs this library's contract, for mindustry-testkit's renderer."
    group = "verification"
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    useJUnitPlatform { includeTags("ui-render") }
    val output = layout.buildDirectory.dir("ui-render")
    systemProperty("xcore.uiRenderOutput", output.get().asFile.absolutePath)
    outputs.dir(output)
    doFirst { delete(output) }
}
