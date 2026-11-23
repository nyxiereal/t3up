plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "meow.nyx.t3up"
    compileSdk { version = release(37) { minorApiLevel = 2 } }

    defaultConfig {
        applicationId = "meow.nyx.t3up"
        minSdk = 31 // dynamic color
        targetSdk = 36
        versionCode = 2
        versionName = "1.1"
    }

    buildFeatures { compose = true }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            // shortcut: signed with the local debug key, add a real keystore if this is ever shared
            signingConfig = signingConfigs.getByName("debug")
        }
    }
}

dependencies {
    implementation("androidx.compose.material3:material3:1.6.0-alpha01")
    implementation("androidx.activity:activity-compose:1.13.0")
}
