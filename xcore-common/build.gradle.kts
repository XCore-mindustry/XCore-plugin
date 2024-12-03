plugins {
    `java-library`
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    // Database
    implementation(libs.mongodb.driver)
    implementation(libs.bson.kotlinx)
    implementation(libs.nitrite.potassium)
    implementation(libs.nitrite.mvstore)
}
