dependencies {
    "api"(project(":libs:mindustry-testkit:core"))
    "compileOnly"("com.github.Anuken.Mindustry:core:v160")
    "compileOnly"("com.github.Anuken.Arc:arc-core:v160")
    "testImplementation"("com.github.Anuken.Mindustry:core:v160")
    "testImplementation"("com.github.Anuken.Arc:arc-core:v160")
    "testImplementation"(project(":libs:mindustry-testkit:fixtures"))
}
