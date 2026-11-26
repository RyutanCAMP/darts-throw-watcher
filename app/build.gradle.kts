plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// 正式版の署名鍵は GitHub のシークレットから CI が渡す（リポジトリには置かない）。無ければ署名なしで作る。
val releaseKeystore: String? = System.getenv("RELEASE_KEYSTORE")

android {
    namespace = "jp.ryutancamp.throwwatcher"
    compileSdk = 34
    defaultConfig {
        applicationId = "jp.ryutancamp.throwwatcher"
        minSdk = 26
        targetSdk = 34
        versionCode = 10
        versionName = "1.0.0"
    }
    signingConfigs {
        if (releaseKeystore != null) {
            create("release") {
                storeFile = file(releaseKeystore)
                storePassword = System.getenv("RELEASE_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("RELEASE_KEY_ALIAS")
                keyPassword = System.getenv("RELEASE_KEY_PASSWORD")
            }
        }
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            if (releaseKeystore != null) signingConfig = signingConfigs.getByName("release")
            resValue("string", "app_name", "投擲ウォッチャー")
        }
        getByName("debug") {
            // テスト版は別アプリとして入る（正式版と並べて入れられる）
            applicationIdSuffix = ".test"
            versionNameSuffix = "-test"
            resValue("string", "app_name", "投擲ウォッチャー(テスト)")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { buildConfig = true }
}
