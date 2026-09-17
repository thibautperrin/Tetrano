plugins {
    alias(libs.plugins.kotlin.jvm)
}

// This module is intentionally free of any Android dependency. The Tetrano
// game engine (board state, line/fork detection, AI) is pure Kotlin so its
// test suite runs on the plain JVM (fast, no emulator) and so nothing in the
// hot path can accidentally pull in an Android type. See docs/PLAN.md §3/§6.

kotlin {
    jvmToolchain(17)
}

dependencies {
    testImplementation(libs.junit)
}

tasks.test {
    useJUnit()
}
