plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.minijarvis.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.minijarvis.app"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true

        // Drop bundled non-English string resources pulled in by libraries
        // (AndroidX, MediaPipe, etc.) — this app's own UI is English-only anyway.
        resourceConfigurations += listOf("en")

        // Nearly every Android phone sold in the last decade is arm64-v8a;
        // x86/x86_64 only exist for emulators and armeabi-v7a is only needed
        // for very old 32-bit-only devices. Restricting to arm64-v8a keeps
        // the debug APK a fraction of its multi-ABI size with no loss of
        // compatibility on real, modern hardware. A release build intended
        // to reach older/32-bit devices should widen this back out.
        ndk {
            abiFilters += listOf("arm64-v8a")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        debug {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    // Core / Compose
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.4")
    implementation("androidx.activity:activity-compose:1.9.1")
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.navigation:navigation-compose:2.7.7")

    // Room (local-only persistence)
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // Encrypted local storage
    implementation("net.zetetic:android-database-sqlcipher:4.5.4")
    implementation("androidx.sqlite:sqlite:2.4.0")
    implementation("androidx.security:security-crypto:1.1.0")

    // CameraX (local, offline capture)
    val cameraxVersion = "1.3.4"
    implementation("androidx.camera:camera-core:$cameraxVersion")
    implementation("androidx.camera:camera-camera2:$cameraxVersion")
    implementation("androidx.camera:camera-lifecycle:$cameraxVersion")
    implementation("androidx.camera:camera-view:$cameraxVersion")

    // ML Kit — bundled, on-device model, no network calls at runtime.
    // (Text recognition was dropped: its bundled OCR pipeline native libraries
    // alone add ~22MB, which was the deciding factor in keeping this APK small.)
    implementation("com.google.mlkit:image-labeling:17.0.9")

    // Background scheduling for local reminders (no network work)
    implementation("androidx.work:work-runtime-ktx:2.9.1")

    // WearOS-free location (on-device only, used solely for local trip/visit logging)
    implementation("com.google.android.gms:play-services-location:21.3.0")

    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // Scoped, revocable folder access for the optional file-access agent tool
    // (Storage Access Framework) — no MANAGE_EXTERNAL_STORAGE special permission.
    implementation("androidx.documentfile:documentfile:1.0.1")

    // Optional local LLM inference (Google's on-device GenAI runtime) — the
    // model weights are never bundled or downloaded by this app; the user
    // supplies a .task model file themselves via the Settings screen.
    implementation("com.google.mediapipe:tasks-genai:0.10.24")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.06.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
