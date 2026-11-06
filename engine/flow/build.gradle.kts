/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * Vendored from Flow-Tube/Flow (GPL-3.0), commit pinned in
 * docs/AUDITORIA_MULTIMOTOR.md. Do not refactor: adaptations only where
 * YewPlay integration requires them (each marked "YewPlay adaptation").
 */

plugins {
    alias(libs.plugins.android.library.classic)
    alias(libs.plugins.jetbrains.kotlinx.serialization)
}

android {
    namespace = "io.github.aedev.flow"
    compileSdk {
        version = release(NEWPIPE_VERSION_SDK_COMPILE_MAJOR) {
            minorApiLevel = NEWPIPE_VERSION_SDK_COMPILE_MINOR
        }
    }

    defaultConfig {
        minSdk {
            version = release(NEWPIPE_VERSION_SDK_MIN)
        }
    }

    buildFeatures {
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    lint {
        // Third-party code vendored as-is: never block YewPlay's build on its lint.
        abortOnError = false
        checkReleaseBuilds = false
    }
}

dependencies {
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.client.encoding)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.squareup.okhttp)
    implementation(libs.newpipe.extractor)
    implementation(libs.androidx.annotation)
}
