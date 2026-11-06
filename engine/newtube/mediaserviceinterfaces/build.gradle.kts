/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * Vendored from aleixrodriala/MediaServiceCore (MIT License), commit pinned in
 * docs/AUDITORIA_MULTIMOTOR.md. Do not refactor: adaptations only where
 * YewPlay integration requires them.
 */

plugins {
    alias(libs.plugins.android.library.classic)
}

android {
    namespace = "com.liskovsoft.mediaserviceinterfaces"
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

dependencies {
    api(projects.engine.newtube.sharedutils)
    implementation(libs.androidx.annotation)
    implementation(libs.reactivex.rxjava)
    implementation(libs.reactivex.rxandroid)
}
