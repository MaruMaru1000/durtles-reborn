plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.example.durtlesreborn.reading.bridge"
    compileSdk {
        version = release(37)
    }
    defaultConfig {
        minSdk = 24
    }
    buildFeatures {
        compose = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    // The app only depends on this module; the shared reading types are exposed through it.
    api(project(":reading-core"))
    implementation(project(":reading-ui"))
    implementation(project(":reading-store"))
    implementation(project(":reading-sources"))
    implementation(libs.androidx.work.runtime.ktx)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.kotlinx.coroutines.core)
    testImplementation(libs.junit)
}
