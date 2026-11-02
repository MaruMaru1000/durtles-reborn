plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.example.durtlesreborn.reading.sources"
    compileSdk {
        version = release(37)
    }
    defaultConfig {
        minSdk = 24
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

dependencies {
    implementation(libs.okhttp)
    implementation(libs.jsoup)
    implementation(libs.kotlinx.coroutines.core)
    testImplementation(libs.junit)
    testImplementation("org.json:json:20240303")
    testImplementation(libs.robolectric)
    testImplementation(libs.okhttp.mockwebserver)
}
