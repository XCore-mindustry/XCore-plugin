plugins {
    `java-library`
    `maven-publish`
}

group = "org.xcore"
version = "0.2.0"

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
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])

            groupId = "org.xcore"
            artifactId = "cloud-mindustry"
            version = "0.2.0"
        }
    }
}
