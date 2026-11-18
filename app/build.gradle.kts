plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// GitHub Actions passes -PverCode=10000+<run number>, so every build is newer than legacy 1xxx builds.
val verCode = (project.findProperty("verCode") as String?)?.toIntOrNull() ?: 1

android {
    namespace = "com.siraanabin.lootbox"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.siraanabin.lootbox"
        minSdk = 29
        targetSdk = 34
        versionCode = verCode
        versionName = "3.$verCode"
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a") }
    }
    packaging {
        jniLibs { useLegacyPackaging = true }
    }

    // In GitHub Actions the signing key comes from repository Secrets (see README).
    // Locally these variables are not set, so Android's normal debug key is used.
    signingConfigs {
        val ks = System.getenv("KEYSTORE_FILE")
        if (ks != null) {
            create("release") {
                storeFile = file(ks)
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        // Keep the APK output path as app-debug.apk for the existing workflow,
        // but sign it with the same persistent key used by the installed app.
        debug {
            if (System.getenv("KEYSTORE_FILE") != null) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
        release {
            isMinifyEnabled = false
            if (System.getenv("KEYSTORE_FILE") != null) {
                signingConfig = signingConfigs.getByName("release")
            }
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
        viewBinding = true
        buildConfig = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.viewpager2:viewpager2:1.1.0")
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")
    implementation("androidx.fragment:fragment-ktx:1.8.2")
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:2.8.4")
    implementation("androidx.media3:media3-exoplayer:1.4.1")
    implementation("androidx.media3:media3-ui:1.4.1")
    implementation("io.github.junkfood02.youtubedl-android:library:0.17.4")
    implementation("io.github.junkfood02.youtubedl-android:ffmpeg:0.17.4")
}
