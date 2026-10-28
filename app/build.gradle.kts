plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.auroratv.launcher"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.auroratv.launcher"
        minSdk = 28
        targetSdk = 33
        versionCode = 2
        versionName = "0.2.0"
    }

    // A fixed, NON-SECRET debug key committed to the repo so that every cloud build is signed
    // identically and can be installed over the previous one (updates keep working).
    signingConfigs {
        getByName("debug") {
            storeFile = file("aurora-debug.keystore")
            storePassword = "android"
            keyAlias = "aurora"
            keyPassword = "android"
        }
    }

    buildTypes {
        release {
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
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
}
