plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "com.tikigsaver.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.tikigsaver.app"
        minSdk = 24
        targetSdk = 35
        versionCode = 8
        versionName = "2.0.5"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    splits {
        abi {
            isEnable = true
            reset()
            include("armeabi-v7a", "arm64-v8a")
            isUniversalApk = false
        }
    }

    signingConfigs {
        create("consistent") {
            storeFile = file("elyrene.keystore")
            storePassword = "elyrenepass"
            keyAlias = "elyrene"
            keyPassword = "elyrenepass"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("consistent")
        }
        release {
            signingConfig = signingConfigs.getByName("consistent")
            isMinifyEnabled = true
            isShrinkResources = true
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
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

val youtubedlAndroidVersion = "0.18.1"

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.activity:activity-compose:1.9.3")

    implementation(platform("androidx.compose:compose-bom:2024.10.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    // On-device yt-dlp + FFmpeg engine (preserves original resolution & 60fps + MP3 extraction)
    implementation("io.github.junkfood02.youtubedl-android:library:$youtubedlAndroidVersion")
    implementation("io.github.junkfood02.youtubedl-android:ffmpeg:$youtubedlAndroidVersion")

    // Networking & JSON parsing (TikTok Photo Mode & Instagram Carousel fallback + direct image/audio download)
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    // Image loading for thumbnails & photo picker grid
    implementation("io.coil-kt:coil-compose:2.7.0")

    // On-device hardware-accelerated Video Trim, Crop & WhatsApp HD export
    implementation("androidx.media3:media3-transformer:1.4.1")
    implementation("androidx.media3:media3-effect:1.4.1")
    implementation("androidx.media3:media3-common:1.4.1")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.10.01"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
