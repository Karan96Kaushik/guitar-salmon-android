plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
}

import com.guitarsalmon.gradle.AppVersioning

// Resolve (and, for assemble/install/bundle, auto-bump) the semantic version before
// the Android plugin stamps versionCode / versionName into the APK.
val appVersion = AppVersioning.resolve(project)

android {
    namespace = "com.guitarsalmon"
    compileSdk = 34

    // Pinned so the native build is reproducible rather than depending on whichever
    // NDK happens to be installed.
    ndkVersion = "26.1.10909125"

    defaultConfig {
        applicationId = "com.guitarsalmon"
        minSdk = 26
        targetSdk = 34
        versionCode = appVersion.code
        versionName = appVersion.name

        externalNativeBuild {
            cmake {
                // Build the app's shared library only; host-side DSP tests are a
                // separate target driven by tools/run_native_tests.sh.
                // Oboe's prefab package is built against the shared STL, so the app
                // has to use it too rather than the NDK's default c++_static.
                arguments += listOf("-DGS_BUILD_ANDROID=ON", "-DANDROID_STL=c++_shared")
            }
        }

        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    externalNativeBuild {
        cmake {
            // Native sources live at the repository root in /cpp.
            path = file("../cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    buildFeatures {
        compose = true
        // Required so CMake can `find_package(oboe)` from the Oboe AAR.
        prefab = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = libs.versions.composeCompiler.get()
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.kotlinx.coroutines.android)

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

    implementation(libs.oboe)
}
