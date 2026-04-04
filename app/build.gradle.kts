fun gitVersionName(): String {
    return try {
        val proc = ProcessBuilder("git", "describe", "--tags", "--always")
            .directory(rootDir)
            .redirectErrorStream(true)
            .start()
        proc.inputStream.bufferedReader().readLine()?.trim()
            ?.removePrefix("v") ?: "0.0.1"
    } catch (_: Exception) { "0.0.1" }
}

fun gitVersionCode(): Int {
    return try {
        val tag = ProcessBuilder("git", "describe", "--tags", "--always", "--abbrev=0")
            .directory(rootDir)
            .redirectErrorStream(true)
            .start()
            .inputStream.bufferedReader().readLine()?.trim()
            ?.removePrefix("v") ?: "0.0.1"
        val parts = tag.split(".").map { it.toIntOrNull() ?: 0 }
        maxOf(1,
            (parts.getOrElse(0) { 0 } * 10000) +
            (parts.getOrElse(1) { 0 } * 100) +
             parts.getOrElse(2) { 0 }
        )
    } catch (_: Exception) { 1 }
}

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.example.ketchup"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.ketchup"
        minSdk = 26
        targetSdk = 36
        versionCode = gitVersionCode()
        versionName = gitVersionName()
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        jvmToolchain(17)
    }

    buildFeatures {
        compose = true
    }

    lint {
        disable += "NullSafeMutableLiveData"
        checkReleaseBuilds = false
    }
}

dependencies {
    // Compose BOM
    val composeBom = platform("androidx.compose:compose-bom:2026.03.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // Activity & Navigation (Compose)
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.navigation:navigation-compose:2.9.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.10.0")
    implementation("androidx.lifecycle:lifecycle-process:2.10.0")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")

    // Serialization (type-safe nav routes)
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.10.0")

    // HTTP
    implementation("com.squareup.okhttp3:okhttp:5.3.0")

    // Coil 3 for Compose (favicons + article images)
    implementation("io.coil-kt.coil3:coil-compose:3.4.0")
    implementation("io.coil-kt.coil3:coil-svg:3.4.0")
    implementation("io.coil-kt.coil3:coil-network-okhttp:3.4.0")

    // Room (offline article cache)
    implementation("androidx.room:room-runtime:2.8.4")
    implementation("androidx.room:room-ktx:2.8.4")
    ksp("androidx.room:room-compiler:2.8.4")

    // WebView (article reader)
    implementation("androidx.webkit:webkit:1.12.1")

    // Security
    implementation("androidx.security:security-crypto:1.1.0")
    implementation("androidx.biometric:biometric:1.1.0")

    // Core
    implementation("androidx.core:core-ktx:1.18.0")
}
