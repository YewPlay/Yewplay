/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * Vendored from aleixrodriala/MediaServiceCore (MIT License), commit pinned in
 * docs/AUDITORIA_MULTIMOTOR.md. Do not refactor: adaptations only where
 * YewPlay integration requires them.
 */

plugins {
    alias(libs.plugins.android.library.classic)
    alias(libs.plugins.jetbrains.kotlin.android)
}

android {
    namespace = "com.liskovsoft.youtubeapi"
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

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    api(projects.engine.newtube.mediaserviceinterfaces)
    implementation(projects.engine.newtube.sharedutils)

    implementation(libs.commons.io)
    implementation(libs.squareup.retrofit)
    implementation(libs.squareup.retrofit.converter.gson)
    implementation(libs.google.gson)
    implementation(libs.jayway.jsonpath)
    implementation(libs.chromium.cronet.api)
    implementation(libs.google.cronet.okhttp)
    implementation(libs.reactivex.rxjava)
    implementation(libs.reactivex.rxandroid)
    implementation(libs.androidx.annotation)
    implementation(libs.androidx.core)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.florianingerl.regex)
    implementation(libs.androidx.webkit)
    implementation(libs.newpipe.nanojson)
    implementation(libs.eclipsesource.j2v8)
}
