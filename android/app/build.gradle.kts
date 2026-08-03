plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)       // required on Kotlin 2.x
    alias(libs.plugins.kotlin.serialization)
    id("com.google.gms.google-services")
    id("kotlin-kapt")
}

android {
    namespace = "com.kreativekoala.riddleverse"
    compileSdk = 35

    signingConfigs {
        create("release") {
            storeFile = file(project.property("RELEASE_STORE_FILE") as String)
            storePassword = project.property("RELEASE_STORE_PASSWORD") as String
            keyAlias = project.property("RELEASE_KEY_ALIAS") as String
            keyPassword = project.property("RELEASE_KEY_PASSWORD") as String
        }
    }

    defaultConfig {
        applicationId = "com.kreativekoala.riddleverse"
        minSdk = 26
        targetSdk = 35
        versionCode = 168
        versionName = "168.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // API base URL for sharing games
        buildConfigField("String", "API_BASE_URL", "\"https://puzzleverseai.com\"")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += setOf(
                "META-INF/versions/9/OSGI-INF/MANIFEST.MF",
                "META-INF/DEPENDENCIES",
                "META-INF/LICENSE",
                "META-INF/LICENSE.txt",
                "META-INF/NOTICE",
                "META-INF/NOTICE.txt"
            )
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    configurations.all {
        exclude(group = "com.intellij", module = "annotations")
        resolutionStrategy.eachDependency {
            if (requested.group == "org.jetbrains.kotlinx" && requested.name.startsWith("kotlinx-serialization")) {
                useVersion(libs.versions.serialization.get())
            }
            if (requested.group == "org.jetbrains.kotlinx" && requested.name.startsWith("kotlinx-coroutines")) {
                useVersion(libs.versions.coroutines.get())
            }
        }
    }
}

dependencies {
    implementation("androidx.webkit:webkit:1.8.0")
    implementation("androidx.browser:browser:1.7.0")

    implementation("io.github.jan-tennert.supabase:supabase-kt:2.0.4")
    implementation("io.github.jan-tennert.supabase:postgrest-kt:2.0.4")

    implementation("io.ktor:ktor-client-android:2.3.7")
    implementation("io.ktor:ktor-client-core:2.3.7")
    implementation("io.ktor:ktor-utils:2.3.7")

    implementation("androidx.navigation:navigation-compose:2.7.6")

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.constraintlayout.compose.android)
    implementation(libs.androidx.storage)

    // Room - ONLY THESE THREE LINES
    implementation(libs.androidx.room.common.jvm)   // if you want common, optional
    kapt(libs.androidx.room.compiler)
    implementation("androidx.room:room-runtime:2.8.1")
    implementation("androidx.room:room-ktx:2.8.1")

    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)

    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:okhttp-sse:4.12.0")

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.compose.material)
    implementation(libs.coil.compose)

    implementation("io.coil-kt:coil-compose:2.5.0")
    implementation("androidx.compose.foundation:foundation:1.5.4")
    implementation("androidx.compose.material3:material3:1.2.0")
    implementation("androidx.compose.material:material-icons-extended:1.6.1")

    implementation(libs.material.icons.extended)
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.compose.foundation:foundation-layout:1.5.4")

    implementation(platform("com.google.firebase:firebase-bom:33.0.0"))
    implementation("com.google.firebase:firebase-auth-ktx")
    implementation("com.google.firebase:firebase-firestore-ktx")
    implementation("com.google.firebase:firebase-perf-ktx")
    implementation("com.google.firebase:firebase-messaging-ktx")

    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation("com.google.code.gson:gson:2.10.1")

    implementation("org.json:json:20231013")
    implementation("com.airbnb.android:lottie-compose:6.1.0")

    implementation("com.google.android.gms:play-services-ads:24.3.0")
    implementation("com.google.android.gms:play-services-auth:20.7.0")

    implementation("com.jakewharton.timber:timber:5.0.1")
    implementation(libs.kotlinx.coroutines.android)

    implementation(libs.androidx.media3.common.ktx)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.ui)
    implementation(libs.play.services.measurement.api)
    implementation(libs.androidx.animation.core.android)
    implementation(libs.androidx.animation.android)
    implementation(libs.androidx.foundation.layout.android)
    implementation(libs.androidx.foundation.android)
    implementation(libs.transport.api)
    implementation(libs.billing.client)
    implementation(libs.unity.ads.mediation)
    implementation("com.android.billingclient:billing-ktx:7.1.1")
    implementation("com.revenuecat.purchases:purchases:8.13.0")
    implementation("com.revenuecat.purchases:purchases-ui:8.13.0")
    implementation(project(":paywallkit"))
    implementation(project(":crosspromokit"))
    implementation(project(":ratingkit"))
    implementation("com.github.tiktok:tiktok-business-android-sdk:1.6.0")
    implementation(libs.play.services.fido)
    implementation(libs.mediation.test.suite)
    implementation(libs.androidx.glance)
    implementation(libs.identity.jvm)
    implementation("androidx.collection:collection-ktx:1.4.0")

    // Accompanist Pager for onboarding tutorial
    implementation("com.google.accompanist:accompanist-pager:0.30.1")
    implementation("com.google.accompanist:accompanist-pager-indicators:0.30.1")

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}