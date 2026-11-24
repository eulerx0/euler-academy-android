plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val envVersionCode = System.getenv("APK_VERSION_CODE")?.toIntOrNull() ?: 1
val envVersionName = System.getenv("APK_VERSION_NAME")?.takeIf { it.isNotBlank() } ?: "1.0.0"

android {
    namespace = "ma.euleracademy.bac2pc"
    compileSdk = 35

    defaultConfig {
        applicationId = "ma.euleracademy.bac2pc"
        minSdk = 24
        targetSdk = 35
        versionCode = envVersionCode
        versionName = envVersionName
    }

    signingConfigs {
        create("release") {
            val ksFile = file("release.jks")
            if (ksFile.exists()) {
                storeFile = ksFile
                storePassword = System.getenv("KEYSTORE_PASSWORD") ?: ""
                keyAlias = System.getenv("KEY_ALIAS") ?: ""
                keyPassword = System.getenv("KEY_PASSWORD") ?: ""
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            val ksFile = file("release.jks")
            if (ksFile.exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
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
        buildConfig = true
    }

    androidResources {
        noCompress += listOf("woff2", "js", "css", "html", "svg", "json")
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.activity:activity-ktx:1.9.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
}
