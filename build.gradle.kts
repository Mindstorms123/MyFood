// Top-level build file where you can add configuration options common to all sub-projects/modules.Add commentMore actions
plugins {
    alias(libs.plugins.android.application) apply false
    //alias(libs.plugins.kotlin.android) apply false
    id("com.google.dagger.hilt.android") version "2.57" apply false
    id("org.jetbrains.kotlin.android") version "2.2.10" apply false // Or a newer compatible version
    id("com.google.devtools.ksp") version "2.2.10-2.0.2" apply true
    id("com.github.ben-manes.versions") version "0.52.0"

}