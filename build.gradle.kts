plugins {
    alias(libs.plugins.android.application) apply false
    // Puts the Kotlin Gradle plugin of the chosen version on the classpath,
    // so AGP's built-in Kotlin and the Compose compiler use the same version.
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.compose.compiler) apply false
}
