plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.commonknowledge.scribbletablet"
    compileSdk = 33

    defaultConfig {
        applicationId = "com.commonknowledge.scribbletablet"
        minSdk = 26
        targetSdk = 33
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.4.8"
    }
    packagingOptions {
        // Resolve duplicate native library conflicts from Onyx SDK and other dependencies
        pickFirst("lib/arm64-v8a/libc++_shared.so")
        pickFirst("lib/armeabi-v7a/libc++_shared.so")
        pickFirst("lib/x86/libc++_shared.so")
        pickFirst("lib/x86_64/libc++_shared.so")
        pickFirst("**/libc++_shared.so")
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)

    // Networking
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.ktor.client.logging)

    // Serialization
    implementation(libs.kotlinx.serialization.json)

    // Image loading
    implementation(libs.coil.compose)

    // Markdown
    implementation(libs.markwon.core)

    // Video (ExoPlayer / Media3)
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.ui)

    // Onyx SDK for e-ink pen drawing (only used on Onyx/BOOX devices)
    implementation("com.onyx.android.sdk:onyxsdk-device:1.2.16") {
        exclude(group = "com.android.support")
    }
    implementation("com.onyx.android.sdk:onyxsdk-pen:1.4.11") {
        // Exclude old support library to avoid conflicts with AndroidX
        exclude(group = "com.android.support")
    }
    // Hidden API bypass - required for Onyx SDK to access pen input on Android 9+
    implementation("org.lsposed.hiddenapibypass:hiddenapibypass:4.3")

    debugImplementation(libs.androidx.ui.tooling)
}
