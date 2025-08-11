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
        minSdk = 26
        targetSdk = 35
        versionCode = 3
        versionName = "2.2"

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
    implementation(platform("androidx.compose:compose-bom:2025.07.00"))

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
    implementation("androidx.activity:activity-compose:1.10.1") // Check if BOM updates this

    // ViewModel for Compose
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.2") // Check if BOM updates this

    // DataStore for local storage
    implementation("androidx.datastore:datastore-preferences:1.1.7")

    // JSON Serialization
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.5.1")

    // CameraX
    // implementation ("com.journeyapps:zxing-android-embedded:4.3.0") // If you choose to use this instead of CameraX + ML Kit
    implementation("com.google.mlkit:barcode-scanning:17.3.0")
    implementation("androidx.camera:camera-core:1.4.2") // Consider using newer versions if available (e.g., 1.3.x)
    implementation("androidx.camera:camera-camera2:1.4.2")
    implementation("androidx.camera:camera-lifecycle:1.4.2")
    implementation("androidx.camera:camera-view:1.4.2")

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
    implementation("androidx.navigation:navigation-compose:2.9.3")

    // Coil for image loading in Compose
    implementation("io.coil-kt:coil-compose:2.6.0")

    // Room components
    implementation("androidx.room:room-runtime:2.7.2") // Ensure this version is correct, consider 2.6.1 if 2.7.1 is alpha/beta and causing issues
    kapt("androidx.room:room-compiler:2.7.2")
    implementation("androidx.room:room-ktx:2.7.2") // Coroutines support
    // Optional Room extensions (include only if you use them)
    // implementation("androidx.room:room-rxjava2:2.7.1")
    // implementation("androidx.room:room-rxjava3:2.7.1")
    // implementation("androidx.room:room-guava:2.7.1")
    testImplementation("androidx.room:room-testing:2.7.2")

    // Remove annotationProcessor if you are using kapt for Room
    // annotationProcessor("androidx.room:room-compiler:2.7.1") // Kapt replaces this for Kotlin

    // Hilt
    implementation("com.google.dagger:hilt-android:2.55")
    kapt("com.google.dagger:hilt-compiler:2.55")

    // Hilt AndroidX extensions (for ViewModel, WorkManager, Navigation)
    implementation("androidx.hilt:hilt-navigation-compose:1.2.0")
    implementation("androidx.hilt:hilt-work:1.2.0")
    kapt("androidx.hilt:hilt-compiler:1.2.0") // This is the compiler for the Hilt AndroidX extensions

    // WorkManager
    implementation ("androidx.work:work-runtime-ktx:2.10.3")

    // Für DataStore (wenn UserPreferencesRepository es verwendet)
    implementation ("androidx.datastore:datastore-preferences:1.1.1")

    //notification-builder
    implementation ("androidx.core:core-ktx:1.13.1")

    //Markdown-Parsing-Bibliothek
    implementation("org.commonmark:commonmark:0.21.0")

    //gson converter
    implementation("com.google.code.gson:gson:2.10.1")
}