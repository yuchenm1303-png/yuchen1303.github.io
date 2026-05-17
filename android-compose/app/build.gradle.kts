plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.yuchen.ailedger"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.yuchen.ailedger"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0-compose-migration"
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.compose.ui:ui-android")
    implementation("androidx.compose.ui:ui-tooling-preview-android")
    implementation("androidx.compose.material3:material3-android")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")

    debugImplementation("androidx.compose.ui:ui-tooling-android")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
