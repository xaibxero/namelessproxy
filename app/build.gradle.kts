plugins {
    id("com.android.application")
}

android {
    namespace = "com.nameless.proxy"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.nameless.proxy"
        minSdk = 29
        targetSdk = 35
        versionCode = 5
        versionName = "2.0.0"

        ndk {
            abiFilters += listOf("arm64-v8a")
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("debug")
            isMinifyEnabled = false
            isShrinkResources = false
            isCrunchPngs = false
        }
        debug {
            isCrunchPngs = false
        }
    }

    androidResources {
        noCompress += listOf("sing-box", "stun-responder", "sh")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation("androidx.webkit:webkit:1.11.0")
}
