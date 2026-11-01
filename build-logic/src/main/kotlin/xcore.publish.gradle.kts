// Publishing target shared by every published component: the XCore Reposilite releases repository.
// Credentials come from xcoreRepositoryReleasesUsername/xcoreRepositoryReleasesPassword
// (ORG_GRADLE_PROJECT_* in CI).
plugins {
    `maven-publish`
}

publishing {
    repositories {
        maven {
            name = "xcoreRepositoryReleases"
            url = uri(providers.gradleProperty("xcoreMavenReleasesUrl").orElse("https://maven.x-core.org/releases").get())
            credentials(PasswordCredentials::class)
            authentication {
                create<BasicAuthentication>("basic")
            }
        }
    }
}

tasks.register("getProjectVersion") {
    val version = project.version.toString()
    doLast { println(version) }
}
