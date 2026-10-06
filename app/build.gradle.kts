plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.sabalapps.arrowescape"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.sabalapps.arrowescape"
        minSdk = 23
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures {
        compose = true
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    testOptions {
        unitTests.all {
            // Forwards the stress flag into the test JVM, which does not
            // inherit Gradle's own system properties. Without it
            // `GeneratorStressTest` skips itself, which is the default:
            //   ./gradlew testDebugUnitTest -Darrowescape.stress=1
            it.systemProperty("arrowescape.stress", System.getProperty("arrowescape.stress") ?: "")
            it.testLogging { showStandardStreams = true }
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    // The Android 12 splash screen, back-ported: one theme on every API level, handed off to Compose.
    implementation(libs.androidx.core.splashscreen)
    // The daily reminder. Unique periodic work: no exact alarms, no server.
    implementation(libs.androidx.work.runtime.ktx)
    // Google's in-app review flow (the Play Store listing is the fallback).
    implementation(libs.play.review)

    val composeBom = platform(libs.androidx.compose.bom)
    implementation(composeBom)
    androidTestImplementation(composeBom)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
}