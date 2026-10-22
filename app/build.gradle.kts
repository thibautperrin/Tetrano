plugins {
    alias(libs.plugins.android.application)
    // No org.jetbrains.kotlin.android plugin: AGP 9+ compiles Kotlin for
    // Android modules itself ("built-in Kotlin"). kotlin-compose and
    // kotlin-serialization remain separate compiler-plugin artifacts and
    // are still applied explicitly.
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.hilt.android)
    alias(libs.plugins.ksp)
}

android {
    namespace = "fr.thibautperrin.tetrano"
    compileSdk = 37

    defaultConfig {
        // Unchanged from the legacy 2015 app on purpose: this is what lets
        // the Play Store treat this rewrite as an update rather than a new
        // listing. See docs/PLAN.md §2 and §9.
        applicationId = "thibautperrin.tetrano"
        minSdk = 26
        targetSdk = 37
        versionCode = 19
        versionName = "5.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    // Lists the 6 languages for Android 13+'s per-app language setting.
    androidResources {
        generateLocaleConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    // Room schema history, used by MigrationTestHelper (see
    // app/src/androidTest and docs/PLAN.md P4) to test every migration,
    // including future ones, against its actual starting schema.
    ksp {
        arg("room.schemaLocation", "$projectDir/schemas")
    }

    lint {
        disable += "PropertyEscape"
        // local.properties is machine-generated, gitignored, and not part of
        // the reviewed source: its exact backslash escaping isn't something
        // this project should gate a build on.
        disable += "ObsoleteSdkInt"
        // mipmap-anydpi-v26 (adaptive icon XML) genuinely requires the -v26
        // qualifier for AAPT to resolve it, even though minSdk is already
        // 26; lint's suggestion to merge it into a plain "anydpi" folder
        // breaks the resource link (AAPT: "resource mipmap/ic_launcher not
        // found"). This matches every AGP project template.
    }
}

dependencies {
    implementation(project(":engine"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.androidx.datastore.preferences)

    implementation(libs.hilt.android)
    implementation(libs.hilt.navigation.compose)
    ksp(libs.hilt.compiler)

    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)

    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.room.testing)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
