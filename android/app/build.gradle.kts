plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "dev.routeplanner"
    // MapLibre Compose 0.19 requires compileSdk 37.
    compileSdk = 37

    defaultConfig {
        applicationId = "dev.routeplanner"
        minSdk = 31
        targetSdk = 36
        versionCode = 1
        versionName = "0.1"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        aidl = true
    }

    // The sample course messages in contract/ are shared with the watch app's unit tests.
    sourceSets["test"].resources.srcDir("../../contract")
}

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.coroutines.android)
    implementation(libs.maplibre.compose)
    runtimeOnly(libs.maplibre.compose.runtime.opengl)
    implementation(libs.connectiq.companion) { artifact { type = "aar" } }

    testImplementation(libs.junit)
    testImplementation(libs.coroutines.test)
    // Reads the sample course messages; android.jar's org.json is only a stub in unit tests.
    testImplementation(libs.serialization.json)

    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.coroutines.test)
}
