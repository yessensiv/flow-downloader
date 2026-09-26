plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace = "app.flow.downloader"
    compileSdk = 35
    defaultConfig {
        applicationId = "app.flow.downloader"
        minSdk = 26
        targetSdk = 35
        versionCode = 20
        versionName = "0.20.0"
        testInstrumentationRunner = "app.flow.downloader.HistoryInstrumentation"
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64") }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    // Trusted Actions builds reuse the existing key so APK updates keep user data.
    System.getenv("FLOW_SIGNING_STORE_FILE")?.let { signingStore ->
        signingConfigs.getByName("debug") {
            storeFile = file(signingStore)
            storePassword = "android"
            keyAlias = "AndroidDebugKey"
            keyPassword = "android"
        }
    }
    packaging { jniLibs.useLegacyPackaging = true }
}
dependencies {
    implementation("io.github.junkfood02.youtubedl-android:library:0.18.1")
    implementation("io.github.junkfood02.youtubedl-android:ffmpeg:0.18.1")
}
