plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "ru.kbr.cargoshare"
    compileSdk = 35
    defaultConfig {
        applicationId = "ru.kbr.cargoshare.referencepreview.compact"
        minSdk = 26
        targetSdk = 35
        versionCode = 38
        versionName = "3.5.2-compact-optimized"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    buildTypes {
        debug {
            // Off by default: 10.0.2.2 exists only inside Android Emulator.
            // Opt in via -PdemoServerEnabled=true for local emulator testing.
            val connectDemoServer = providers.gradleProperty("demoServerEnabled").orNull == "true"
            buildConfigField("boolean", "DEMO_SERVER_ENABLED", connectDemoServer.toString())
            buildConfigField("String", "API_URL", "\"http://10.0.2.2:8000\"")
        }
        release {
            buildConfigField("boolean", "DEMO_SERVER_ENABLED", "false")
            isMinifyEnabled = false
            buildConfigField("String", "API_URL", "\"https://api.example.invalid\"")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.10.01")
    implementation(composeBom)
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("org.osmdroid:osmdroid-android:6.1.20")
    implementation("androidx.core:core-ktx:1.15.0")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.uiautomator:uiautomator:2.3.0")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
