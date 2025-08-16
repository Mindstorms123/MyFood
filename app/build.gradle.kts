@file:Suppress("DEPRECATION")

plugins {
    alias(libs.plugins.android.application)
    // Or id("com.android.application")
    alias(libs.plugins.kotlin.android)    // Or id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization") version "2.2.10"
    //id("kotlin-kapt")
    //id("org.jetbrains.kotlin.android") version "2.1.21" apply false
    id ("com.google.dagger.hilt.android")
    id("com.google.devtools.ksp") version "2.2.10-2.0.2" apply true
    id("com.github.ben-manes.versions") version "0.52.0"
}

android {
    namespace = "com.example.myfood"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.myfood"
        minSdk = 26
        //noinspection OldTargetApi
        targetSdk = 35
        versionCode = 4
        versionName = "2.3"

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
        freeCompilerArgs = listOf("-XXLanguage:+PropertyParamAnnotationDefaultTargetMode")
    }

    buildFeatures {
        compose = true
    }

    @Suppress("UnstableApiUsage")
    composeOptions {
        kotlinCompilerExtensionVersion = "2.2.10" // Consider aligning this with your Kotlin plugin version if issues arise
    }

}

kotlin {
    jvmToolchain(21) // Aligned with Java version
    //sourceSets.all {
    //languageSettings.optIn("androidx.compose.material.ExperimentalMaterialApi")
    //languageSettings.optIn("kotlinx.serialization.ExperimentalSerializationApi")
}

// Allow references to generated code
/*kapt {
    correctErrorTypes = true
}*/


dependencies {
    // Compose BOM – centralizes versioning
    implementation(platform(libs.androidx.compose.bom))

    // Compose UI
    //noinspection UseTomlInstead
    implementation("androidx.compose.ui:ui")
    //noinspection UseTomlInstead
    implementation("androidx.compose.ui:ui-tooling-preview")
    //noinspection UseTomlInstead
    debugImplementation("androidx.compose.ui:ui-tooling")

    // Compose Material 3 (modern design)
    //noinspection UseTomlInstead
    implementation("androidx.compose.material3:material3") // Main M3 library
    // implementation("androidx.compose.material3:material3:1.2.1") // This is redundant if using BOM and the above line

    // Compose Material (for specific components like SwipeToDismiss if not yet in M3)
    //noinspection UseTomlInstead
    implementation("androidx.compose.material:material")
    //noinspection UseTomlInstead
    implementation("androidx.compose.material:material-icons-extended") // BOM should manage version
    implementation(libs.material)

    // Activity for Compose
    implementation(libs.androidx.activity.compose) // Check if BOM updates this

    // ViewModel for Compose
    implementation(libs.androidx.lifecycle.viewmodel.compose) // Check if BOM updates this

    // DataStore for local storage
    //noinspection UseTomlInstead
    implementation("androidx.datastore:datastore-preferences:1.1.7")

    // JSON Serialization
    implementation(libs.kotlinx.serialization.json)

    // CameraX
    // implementation ("com.journeyapps:zxing-android-embedded:4.3.0") // If you choose to use this instead of CameraX + ML Kit
    implementation(libs.google.mlkit.barcodeScanning)
    implementation(libs.androidx.camera.core) // Consider using newer versions if available (e.g., 1.3.x)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)

    // Ktor (HTTP Client)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.cio)
    implementation(libs.ktor.client.contentnegotiation)
    implementation(libs.ktor.serialization.kotlinx.json)

    // Android Core & AppCompat (libs should be defined in your version catalog)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat) // Potentially removable if fully Compose and not using AppCompat themes/components directly
    // implementation(libs.material) // For View system Material, potentially removable

    // Navigation Compose
    implementation(libs.androidx.navigation.compose)

    // Coil for image loading in Compose
    implementation(libs.coil.compose)

    // Room components
    implementation(libs.androidx.room.runtime) // Ensure this version is correct, consider 2.6.1 if 2.7.1 is alpha/beta and causing issues
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.room.ktx) // Coroutines support
    // Optional Room extensions (include only if you use them)
    // implementation("androidx.room:room-rxjava2:2.7.1")
    // implementation("androidx.room:room-rxjava3:2.7.1")
    // implementation("androidx.room:room-guava:2.7.1")
    //noinspection UseTomlInstead
    testImplementation("androidx.room:room-testing:2.7.2")

    // Remove annotationProcessor if you are using kapt for Room
    // annotationProcessor("androidx.room:room-compiler:2.7.1") // Kapt replaces this for Kotlin

    // Hilt
    implementation(libs.google.dagger.hilt.android)
    ksp(libs.google.dagger.hilt.compiler)

    // Hilt AndroidX extensions (for ViewModel, WorkManager, Navigation)
    implementation(libs.androidx.hilt.navigation.compose)
    //noinspection UseTomlInstead
    implementation("androidx.hilt:hilt-work:1.2.0")
    //noinspection UseTomlInstead
    ksp("androidx.hilt:hilt-compiler:1.2.0") // This is the compiler for the Hilt AndroidX extensions

    // WorkManager
    //noinspection UseTomlInstead
    implementation ("androidx.work:work-runtime-ktx:2.10.3")

    // Für DataStore (wenn UserPreferencesRepository es verwendet)
    //noinspection UseTomlInstead
    implementation ("androidx.datastore:datastore-preferences:1.1.7")

    //notification-builder
    implementation (libs.androidx.core.ktx)

    //Markdown-Parsing-Bibliothek
    //noinspection UseTomlInstead
    implementation("org.commonmark:commonmark:0.25.1")

    //gson converter
    //noinspection UseTomlInstead
    implementation("com.google.code.gson:gson:2.13.1")
}