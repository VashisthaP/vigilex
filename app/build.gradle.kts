import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
    alias(libs.plugins.google.services)
}

// Read keys from local.properties — never hardcode
val localProperties = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) load(f.inputStream())
}

android {
    namespace = "com.vigilex"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.vigilex"
        minSdk = 26
        targetSdk = 35
        versionCode = 2
        versionName = "1.0.1"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Ensure 64-bit support for modern phones (S24, OnePlus, etc.)
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64", "x86")
        }

        // Inject keys into BuildConfig and manifest placeholders
        buildConfigField("String", "MAPS_API_KEY", "\"${localProperties["MAPS_API_KEY"] ?: ""}\"")
        buildConfigField("String", "SUPER_ADMIN_EMAIL", "\"${localProperties["SUPER_ADMIN_EMAIL"] ?: ""}\"")
        buildConfigField("String", "SUPER_ADMIN_PHONE", "\"${localProperties["SUPER_ADMIN_PHONE"] ?: ""}\"")
        manifestPlaceholders["mapsApiKey"] = localProperties["MAPS_API_KEY"] ?: ""
    }

    signingConfigs {
        create("release") {
            storeFile     = file(localProperties["KEYSTORE_PATH"] as? String ?: "")
            storePassword = localProperties["KEYSTORE_PASSWORD"] as? String ?: ""
            keyAlias      = localProperties["KEY_ALIAS"] as? String ?: ""
            keyPassword   = localProperties["KEY_PASSWORD"] as? String ?: ""
            enableV1Signing = true
            enableV2Signing = true
            enableV3Signing = true
            enableV4Signing = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig   = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
        // Opt-in for Material3 experimental APIs (TopAppBar, NavigationBar, etc.)
        freeCompilerArgs += listOf(
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api"
        )
    }

    // ── Fix "Unable to strip .so libraries" ──────────────────────────────
    // NDK is not required at build time — ML Kit and AndroidX ship pre-stripped
    // release .so files. Telling the packager to keep them as-is avoids the
    // strip step entirely, which fails when NDK tools aren't present.
    packaging {
        jniLibs {
            keepDebugSymbols += setOf("**/*.so")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.service)   // LifecycleService for MonitoringForegroundService
    implementation(libs.androidx.activity.compose)

    // Compose
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)   // Bluetooth, etc.
    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)

    // Navigation
    implementation(libs.navigation.compose)
    implementation(libs.hilt.navigation.compose)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    // Room
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    // Firebase
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth.ktx)
    implementation(libs.firebase.firestore.ktx)
    implementation(libs.firebase.messaging.ktx)

    // Maps & Location
    implementation(libs.play.services.maps)
    implementation(libs.play.services.location)
    implementation(libs.places)
    implementation(libs.maps.compose)

    // CameraX
    implementation(libs.camerax.core)
    implementation(libs.camerax.camera2)
    implementation(libs.camerax.lifecycle)
    implementation(libs.camerax.view)
    implementation(libs.concurrent.futures.ktx)
    implementation(libs.guava.listenablefuture)

    // ML Kit Face Detection (on-device — no network call)
    implementation(libs.mlkit.face.detection)

    // WorkManager
    implementation(libs.work.runtime.ktx)
    implementation(libs.hilt.work)
    ksp(libs.hilt.work.compiler)

    // Coroutines
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.play.services)

    // Runtime permissions helper
    implementation(libs.accompanist.permissions)

    // Unit tests (local JVM)
    testImplementation("junit:junit:4.13.2")
}
