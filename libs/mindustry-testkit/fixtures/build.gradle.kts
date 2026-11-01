dependencies {
    api(project(":libs:mindustry-testkit:core"))

    compileOnly("com.github.Anuken.Mindustry:core:v160")
    compileOnly("com.github.Anuken.Arc:arc-core:v160")
    compileOnly("com.github.Anuken.Arc:g3d:v160")

    compileOnly(platform(libs.junit.bom))
    compileOnly("org.junit.jupiter:junit-jupiter-api")

    testImplementation("com.github.Anuken.Mindustry:core:v160")
    testImplementation("com.github.Anuken.Arc:arc-core:v160")
    testImplementation("com.github.Anuken.Arc:g3d:v160")
    testImplementation("org.assertj:assertj-core:3.27.7")
}
