plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.boxmemo.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.boxmemo.app"
        minSdk = 29
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
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
        aidl = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
        jniLibs {
            pickFirsts += "**/*.so"
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.activity:activity-compose:1.9.3")

    val composeBom = platform("androidx.compose:compose-bom:2024.10.01")
    implementation(composeBom)
    androidTestImplementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // Onyx HWR (MyScript) access for U7 — AIDL binding to the built-in
    // recognizer, matching jdkruzr/aragonite's confirmed-working approach.
    implementation("com.onyx.android.sdk:onyxsdk-device:1.3.2") {
        exclude(group = "com.android.support", module = "support-compat")
    }
    implementation("com.onyx.android.sdk:onyxsdk-pen:1.5.1") {
        exclude(group = "com.android.support", module = "support-compat")
        exclude(group = "com.android.support", module = "appcompat-v7")
        exclude(group = "com.onyx.android.sdk", module = "onyxsdk-geometry")
    }
    implementation("com.onyx.android.sdk:onyxsdk-base:1.8.3") {
        exclude(group = "com.android.support", module = "support-compat")
        exclude(group = "com.android.support", module = "appcompat-v7")
    }
    implementation("org.lsposed.hiddenapibypass:hiddenapibypass:6.1") // required by onyx sdk

    // Google ML Kit Digital Ink Recognition — on-device, offline, no firmware
    // internals. Selectable alternative to the Onyx MyScript engine for
    // comparison. Pulls com.google.android.gms:play-services-tasks transitively.
    implementation("com.google.mlkit:digital-ink-recognition:19.0.0")

    // Jetpack Ink — e-ink-friendly stroke rendering (U6), matching Aragonite.
    implementation("androidx.ink:ink-nativeloader:1.0.0")
    implementation("androidx.ink:ink-brush:1.0.0")
    implementation("androidx.ink:ink-geometry:1.0.0")
    implementation("androidx.ink:ink-rendering:1.0.0")
    implementation("androidx.ink:ink-strokes:1.0.0")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
}
