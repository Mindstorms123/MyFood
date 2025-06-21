plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    id("org.jetbrains.kotlin.plugin.compose") // falls du Compose Multiplatform nutzt
    id("org.jetbrains.kotlin.plugin.serialization") version "1.9.21"
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
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlinOptions {
        jvmTarget = "11"
    }

    buildFeatures {
        compose = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.12"
    }
}

dependencies {
    // Compose BOM – zentralisierte Versionierung
    implementation(platform("androidx.compose:compose-bom:2024.05.00"))

    // Compose UI
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // Compose Material 3 (modernes Design)
    implementation("androidx.compose.material3:material3")

    // Compose Material 2 (für SwipeToDismiss etc.)
    implementation("androidx.compose.material:material")

    // Activity für Compose
    implementation("androidx.activity:activity-compose:1.8.2")

    // ViewModel für Compose
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.6.2")

    // DataStore für lokale Speicherung
    implementation("androidx.datastore:datastore-preferences:1.0.0")

    // JSON-Serialisierung
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.5.1")

    // ZXing Barcode Scanner
   // implementation ("com.journeyapps:zxing-android-embedded:4.3.0")

    // Für Compose Activity Result API (normalerweise schon drin)
    implementation ("androidx.activity:activity-compose:1.7.0")
    // Kamera
    implementation ("androidx.compose.material:material-icons-extended:<version>")
    implementation ("com.google.mlkit:barcode-scanning:17.0.3")
    implementation ("androidx.camera:camera-core:1.2.3")
    implementation ("androidx.camera:camera-camera2:1.2.3")
    implementation ("androidx.camera:camera-lifecycle:1.2.3")
    implementation ("androidx.camera:camera-view:1.2.3")

    // BarCode
    implementation ("io.ktor:ktor-client-core:2.3.4")
    implementation ("io.ktor:ktor-client-cio:2.3.4")
    implementation ("io.ktor:ktor-client-content-negotiation:2.3.4")
    implementation ("io.ktor:ktor-serialization-kotlinx-json:2.3.4")

    // Android Core & AppCompat
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)

    // Bottom Navigation Bar
    implementation("androidx.navigation:navigation-compose:2.7.7") // Oder die neueste Version
    implementation("androidx.compose.material3:material3:1.2.1") // Sicherstellen, dass Material 3 aktuell ist
    implementation("io.coil-kt:coil-compose:2.6.0")

    //Kotlin reflektion
    implementation("org.jetbrains.kotlin:kotlin-reflect")
}

kotlin {
    sourceSets.all {
        languageSettings.optIn("androidx.compose.material.ExperimentalMaterialApi")
        languageSettings.optIn("kotlinx.serialization.ExperimentalSerializationApi")
    }
}
