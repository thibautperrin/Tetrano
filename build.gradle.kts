// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    // :app has no org.jetbrains.kotlin.android plugin: AGP 9+ compiles its
    // Kotlin sources itself ("built-in Kotlin"). :engine is a plain Kotlin/
    // JVM module and applies kotlin-jvm directly.
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.hilt.android) apply false
    alias(libs.plugins.ksp) apply false
}
