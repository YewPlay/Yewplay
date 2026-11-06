/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * Vendored from aleixrodriala/SharedModules (MIT License), commit pinned in
 * docs/AUDITORIA_MULTIMOTOR.md. Do not refactor: adaptations only where
 * YewPlay integration requires them.
 */

plugins {
    alias(libs.plugins.android.library.classic)
}

android {
    namespace = "com.liskovsoft.sharedutils"
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
    implementation(libs.androidx.annotation)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.core)
    implementation(libs.androidx.preference)
    implementation(libs.commons.io)
    implementation(libs.squareup.okhttp)
    implementation(libs.squareup.okhttp.dnsoverhttps)
    implementation(libs.squareup.okhttp.logging)
    implementation(libs.google.gson)
    implementation(libs.chromium.cronet.api)
    compileOnly(libs.chromium.cronet.embedded)
    implementation(libs.brotli.dec)
    implementation(libs.dnsjava)
    implementation(libs.jakewharton.disklrucache)
    implementation(libs.reactivex.rxjava)
    implementation(libs.reactivex.rxandroid)
}
