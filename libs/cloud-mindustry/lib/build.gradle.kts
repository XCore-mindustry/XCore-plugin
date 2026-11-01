plugins {
    `java-library`
    id("xcore.java17")
    id("xcore.publish")
}

java {
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

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.mindustry.core)
    testImplementation(libs.arc.core)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.named<Test>("test") {
    useJUnitPlatform()
    testLogging {
        showStandardStreams = true
    }
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])

            groupId = "org.xcore"
            artifactId = "cloud-mindustry"
            version = project.version.toString()
        }
    }
}
