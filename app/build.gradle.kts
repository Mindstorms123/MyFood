plugins {
     alias(libs.plugins.android.application)
    // Or id("com.android.application")
    alias(libs.plugins.kotlin.android)    // Or id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization") version "2.1.21"
    id("kotlin-kapt")
    //id("org.jetbrains.kotlin.android") version "2.1.21" apply false
    id ("com.google.dagger.hilt.android")
}

android {
    namespace = "com.example.myfood"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.myfood"
        minSdk = 24
        targetSdk = 35
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
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    kotlinOptions {
        jvmTarget = "21"
    }

    buildFeatures {
        compose = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.12" // Consider aligning this with your Kotlin plugin version if issues arise
    }
}

kotlin {
    jvmToolchain(21) // Aligned with Java version
    //sourceSets.all {
        //languageSettings.optIn("androidx.compose.material.ExperimentalMaterialApi")
        //languageSettings.optIn("kotlinx.serialization.ExperimentalSerializationApi")
    }

// Allow references to generated code
kapt {
    correctErrorTypes = true
}


dependencies {
    // Compose BOM – centralizes versioning
    implementation(platform("androidx.compose:compose-bom:2024.05.00"))

    // Compose UI
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // Compose Material 3 (modern design)
    implementation("androidx.compose.material3:material3") // Main M3 library
    // implementation("androidx.compose.material3:material3:1.2.1") // This is redundant if using BOM and the above line

    // Compose Material (for specific components like SwipeToDismiss if not yet in M3)
    implementation("androidx.compose.material:material")
    implementation("androidx.compose.material:material-icons-extended") // BOM should manage version
    implementation("com.google.android.material:material:1.12.0")

    // Activity for Compose
    implementation("androidx.activity:activity-compose:1.8.2") // Check if BOM updates this

    // ViewModel for Compose
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.6.2") // Check if BOM updates this

    // DataStore for local storage
    implementation("androidx.datastore:datastore-preferences:1.0.0")

    // JSON Serialization
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.5.1")

    // CameraX
    // implementation ("com.journeyapps:zxing-android-embedded:4.3.0") // If you choose to use this instead of CameraX + ML Kit
    implementation("com.google.mlkit:barcode-scanning:17.0.3")
    implementation("androidx.camera:camera-core:1.2.3") // Consider using newer versions if available (e.g., 1.3.x)
    implementation("androidx.camera:camera-camera2:1.2.3")
    implementation("androidx.camera:camera-lifecycle:1.2.3")
    implementation("androidx.camera:camera-view:1.2.3")

    // Ktor (HTTP Client)
    implementation("io.ktor:ktor-client-core:2.3.4")
    implementation("io.ktor:ktor-client-cio:2.3.4")
    implementation("io.ktor:ktor-client-content-negotiation:2.3.4")
    implementation("io.ktor:ktor-serialization-kotlinx-json:2.3.4")

    // Android Core & AppCompat (libs should be defined in your version catalog)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat) // Potentially removable if fully Compose and not using AppCompat themes/components directly
    // implementation(libs.material) // For View system Material, potentially removable

    // Navigation Compose
    implementation("androidx.navigation:navigation-compose:2.7.7")

    // Coil for image loading in Compose
    implementation("io.coil-kt:coil-compose:2.6.0")

    // Room components
    implementation("androidx.room:room-runtime:2.7.1") // Ensure this version is correct, consider 2.6.1 if 2.7.1 is alpha/beta and causing issues
    kapt("androidx.room:room-compiler:2.7.1")
    implementation("androidx.room:room-ktx:2.7.1") // Coroutines support
    // Optional Room extensions (include only if you use them)
    // implementation("androidx.room:room-rxjava2:2.7.1")
    // implementation("androidx.room:room-rxjava3:2.7.1")
    // implementation("androidx.room:room-guava:2.7.1")
    testImplementation("androidx.room:room-testing:2.7.1")

    // Remove annotationProcessor if you are using kapt for Room
    // annotationProcessor("androidx.room:room-compiler:2.7.1") // Kapt replaces this for Kotlin

    // hilt
    implementation("com.google.dagger:hilt-android:2.51.1") // Deine Hilt Version
    kapt("com.google.dagger:hilt-compiler:2.51.1")     // Deine Hilt Version
    implementation("androidx.hilt:hilt-navigation-compose:1.2.0") // oder die aktuellste Version


}
