plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val releaseKeystorePath =
    System.getenv("ANDROID_KEYSTORE_PATH")
        ?.takeIf { it.isNotBlank() }

android {
    namespace = "com.nkiridown.compat"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.nkiridown.compat"
        minSdk = 29
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
    }

    compileOptions {
        sourceCompatibility =
            JavaVersion.VERSION_17
        targetCompatibility =
            JavaVersion.VERSION_17
    }

    @Suppress("DEPRECATION")
    kotlinOptions {
        jvmTarget = "17"
    }

    signingConfigs {
        if (releaseKeystorePath != null) {
            create("release") {
                storeFile =
                    file(releaseKeystorePath)
                storePassword =
                    System.getenv(
                        "ANDROID_KEYSTORE_PASSWORD"
                    )
                keyAlias =
                    System.getenv(
                        "ANDROID_KEY_ALIAS"
                    )
                keyPassword =
                    System.getenv(
                        "ANDROID_KEY_PASSWORD"
                    )
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false

            if (releaseKeystorePath != null) {
                signingConfig =
                    signingConfigs
                        .getByName("release")
            }
        }
    }

    splits {
        abi {
            isEnable = true
            reset()
            include(
                "arm64-v8a",
                "armeabi-v7a"
            )
            isUniversalApk = false
        }
    }

    packaging {
        resources {
            excludes +=
                "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(
        "androidx.core:core-ktx:1.15.0"
    )
    implementation(
        "org.videolan.android:libvlc-all:3.7.6"
    )
}
