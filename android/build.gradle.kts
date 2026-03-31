plugins {
    alias(libs.plugins.android.application) apply false
    id("com.android.library") version "8.8.0" apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.google.services) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.kotlin.multiplatform) apply false
}

buildscript {
    dependencies {
        classpath("com.google.gms:google-services:4.4.1")
    }
}
