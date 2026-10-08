plugins {
    alias(libs.plugins.kotlin.jvm)
    application
}

// Command-line tools for training the level-101 network (see tools/nn/README.md):
// self-play data generation, arena matches, matches against the legacy AI.
// Not part of the app: it only depends on :engine, so it runs on the plain JVM.

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":engine"))
}

application {
    mainClass.set("fr.thibautperrin.tetrano.selfplay.MainKt")
    applicationDefaultJvmArgs = listOf("-Xmx6g")
}
