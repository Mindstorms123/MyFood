// Top-level build file where you can add configuration options common to all sub-projects/modules.Add commentMore actions
plugins {
    alias(libs.plugins.android.application) apply false
    //alias(libs.plugins.kotlin.android) apply false
    id("com.google.dagger.hilt.android") version "2.51" apply false
    id("org.jetbrains.kotlin.android") version "2.1.21" apply false // Or a newer compatible version
    id("com.google.devtools.ksp") version "1.9.0-1.0.13" apply false

}