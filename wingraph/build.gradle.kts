plugins {
    alias(libs.plugins.kotlin.jvm)
    application
}

// Generator of the winning graph of the Unbeatable level (see docs/WINNING_GRAPH_PLAN.md and
// tools/wingraph/README.md). Not part of the app: it only depends on :engine, so it runs on the
// plain JVM.

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":engine"))
    testImplementation(libs.junit)
}

application {
    mainClass.set("fr.thibautperrin.tetrano.wingraph.MainKt")
    applicationDefaultJvmArgs = listOf("-Xmx12g")
}

tasks.test {
    useJUnit()
    maxHeapSize = "3g"
}
