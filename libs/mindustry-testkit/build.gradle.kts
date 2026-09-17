plugins { base }

subprojects {
    apply(plugin = "java-library")
    apply(plugin = "maven-publish")

    group = "org.xcore.testkit"
    version = "0.1.0-SNAPSHOT"

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
        "testImplementation"(platform("org.junit:junit-bom:5.12.1"))
        "testImplementation"("org.junit.jupiter:junit-jupiter")
        "testRuntimeOnly"("org.junit.platform:junit-platform-launcher")
    }
    tasks.withType<Test>().configureEach { useJUnitPlatform() }
    extensions.configure<PublishingExtension> {
        publications {
            create<MavenPublication>("maven") { from(components["java"]) }
        }
    }
}
