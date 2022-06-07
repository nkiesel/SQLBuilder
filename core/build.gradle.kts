dependencies {
    implementation(libs.slf4j.api)
    implementation(libs.logback.classic)
    implementation(libs.commons.codec)
    implementation(libs.kotlin.logging.jvm)
    implementation(libs.kotlin.reflect)

    testImplementation(libs.junit.jupiter.api)
    testRuntimeOnly(libs.junit.jupiter.engine)
}
