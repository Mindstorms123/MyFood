plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization") version "1.9.21" // Stelle sicher, dass diese Kotlin-Version passt
    id("com.google.dagger.hilt.android") // Version wird typischerweise aus dem Projekt-Level Plugin Block geerbt
    // id("kotlin-ksp") // << ENTFERNEN
    id("com.google.devtools.ksp") version "1.9.21-1.0.16" // << KORREKT: Füge hier eine passende KSP Version hinzu
    //    Die Version sollte zu deiner Kotlin-Version (1.9.21) passen.
    //    Prüfe die KSP GitHub Releases für die genaue Version.
    //    z.B. für Kotlin 1.9.21 ist "1.9.21-1.0.16" oder "1.9.21-1.0.17" gängig.
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
        kotlinCompilerExtensionVersion = "1.5.12" // Überprüfe, ob diese Version zu deiner Compose BOM / Kotlin-Version passt
        // Für Kotlin 1.9.21 und Compose BOM 2024.05.00 ist eher 1.5.8 oder höher (bis 1.5.12)
        //  https://developer.android.com/jetpack/androidx/releases/compose-compiler
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
    implementation("androidx.compose.material:material") // Normalerweise nicht mehr nötig, wenn Material3 voll genutzt wird

    // Activity für Compose
    implementation("androidx.activity:activity-compose:1.8.2") // Aktuell

    // ViewModel für Compose
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0") // Aktuell für lifecycle, älter in deiner Datei (2.6.2)

    // DataStore für lokale Speicherung
    implementation("androidx.datastore:datastore-preferences:1.0.0") // Ist ok, 1.1.1 ist neuer

    // JSON-Serialisierung
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3") // Deine Version 1.5.1 ist älter, update empfohlen

    // Kamera
    // Stelle sicher, dass <version> ersetzt wird und die Versionen aktuell/kompatibel sind
    // implementation("androidx.compose.material:material-icons-extended:<version>") // z.B. implementation("androidx.compose.material:material-icons-extended") aus Compose BOM
    implementation("com.google.mlkit:barcode-scanning:17.2.0") // Deine ist 17.0.3
    implementation("androidx.camera:camera-core:1.3.3") // Deine sind 1.2.3
    implementation("androidx.camera:camera-camera2:1.3.3")
    implementation("androidx.camera:camera-lifecycle:1.3.3")
    implementation("androidx.camera:camera-view:1.3.3")

    // Ktor (Deine Versionen sind ok, aber neuere gibt es, z.B. 2.3.9 oder 2.3.10)
    implementation("io.ktor:ktor-client-core:2.3.4")
    implementation("io.ktor:ktor-client-cio:2.3.4") // Erwäge ktor-client-android für Android
    implementation("io.ktor:ktor-client-content-negotiation:2.3.4")
    implementation("io.ktor:ktor-serialization-kotlinx-json:2.3.4")

    // Android Core & AppCompat
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat) // Wird bei reinen Compose-Apps seltener direkt benötigt
    implementation(libs.material) // Material Design Components (View-basiert), ggf. nicht nötig bei reinen Compose-Apps

    // Bottom Navigation Bar
    implementation("androidx.navigation:navigation-compose:2.7.7")
    // implementation("androidx.compose.material3:material3:1.2.1") // Wird schon über BOM verwaltet, diese Zeile ist redundant und kann zu Konflikten führen
    implementation("io.coil-kt:coil-compose:2.6.0")

    //Kotlin reflektion
    implementation("org.jetbrains.kotlin:kotlin-reflect") // Version sollte zur Kotlin-Version passen, z.B. 1.9.21

    //ViewModel (Deine Versionen sind ok)
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    // implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0") // Doppelt, schon oben (2.6.2 war älter)

    // Room
    implementation("androidx.room:room-runtime:2.6.1") // Deine Version 2.6.0 ist ok, 2.6.1 ist aktuell
    implementation("androidx.room:room-ktx:2.6.1")
    // annotationProcessor("androidx.room:room-compiler:2.6.0") // << ÄNDERN, wenn du KSP nutzt
    ksp("androidx.room:room-compiler:2.6.1") // << VERWENDE KSP für Room, wenn du KSP für Hilt nutzt

    // Hilt
    implementation("com.google.dagger:hilt-android:2.51") // Oder aktuellere Version
    ksp("com.google.dagger:hilt-compiler:2.51")    // Stelle sicher, dass diese Version mit der Implementierung übereinstimmt
    // ksp("androidx.room:room-compiler:2.6.1") // Doppelt, schon oben bei Room

    // Für hiltViewModel() in Compose
    implementation("androidx.hilt:hilt-navigation-compose:1.2.0") // Oder aktuellere Version
}

kotlin {
    sourceSets.all {
        languageSettings.optIn("androidx.compose.material.ExperimentalMaterialApi") // Ist ok für Material 2, bei reinem M3 weniger relevant
        languageSettings.optIn("kotlinx.serialization.ExperimentalSerializationApi")
    }
}