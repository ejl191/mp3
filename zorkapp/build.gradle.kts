plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// The game itself is zork/index.html, copied into src/main/assets by zork/build.py.
// This module is a full-screen WebView around it. It shares the Drive Player signing key.
val buildNumber = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1

android {
    namespace = "com.ejl191.zork"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.ejl191.zork"
        minSdk = 26
        targetSdk = 34
        versionCode = buildNumber
        versionName = "1.0.$buildNumber"
    }

    signingConfigs {
        create("shared") {
            storeFile = rootProject.file(System.getenv("SIGNING_KEYSTORE") ?: "keystore/driveplayer.jks")
            storePassword = System.getenv("SIGNING_STORE_PASSWORD") ?: "driveplayer"
            keyAlias = System.getenv("SIGNING_KEY_ALIAS") ?: "driveplayer"
            keyPassword = System.getenv("SIGNING_KEY_PASSWORD") ?: "driveplayer"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("shared")
        }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("shared")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-ktx:1.9.2")
    implementation("androidx.webkit:webkit:1.11.0")
}
