import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.secrets)
}

android {
    namespace = "com.mytm.darrbi"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.mytm.darrbi"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Constant across environments.
        buildConfigField("String", "RENTAL_URL", "\"http://ride-aride.xintdev.com/api/ride/\"")
    }

    androidResources {
        // EN/AR only.
        localeFilters += listOf("en", "ar")
    }

    flavorDimensions += "env"
    productFlavors {
        create("dev") {
            dimension = "env"
            buildConfigField("String", "MAIN_URL", "\"https://api-darbi.xintdev.com\"")
            buildConfigField("String", "CMS_URL", "\"https://cms-darbi.xintdev.com\"")
            buildConfigField("String", "DASHBOARD_URL", "\"https://admin-darbi.xintdev.com\"")
//            buildConfigField("String", "MAIN_URL", "\"http://150.230.54.239:3000\"")
//            buildConfigField("String", "CMS_URL", "\"https://cmsride.xintdev.com\"")
//            buildConfigField("String", "DASHBOARD_URL", "\"http://dev-dashboard.ride.sa\"")
        }
        create("uat") {
            dimension = "env"
            buildConfigField("String", "MAIN_URL", "\"https://api-darbi.xintdev.com\"")
            buildConfigField("String", "CMS_URL", "\"https://cms-darbi.xintdev.com\"")
            buildConfigField("String", "DASHBOARD_URL", "\"https://admin-darbi.xintdev.com\"")
        }
        create("preprod") {
            dimension = "env"
            buildConfigField("String", "MAIN_URL", "\"https://pre-prod-apis.ride.sa\"")
            buildConfigField("String", "CMS_URL", "\"https://prod-cms.ride.sa\"")
            buildConfigField("String", "DASHBOARD_URL", "\"https://pre-prod-dashboard.ride.sa\"")
        }
        create("production") {
            dimension = "env"
            buildConfigField("String", "MAIN_URL", "\"https://prodapi.ride.sa\"")
            buildConfigField("String", "CMS_URL", "\"https://cmsride.xintdev.com\"")
            buildConfigField("String", "DASHBOARD_URL", "\"https://proddashboard.ride.sa\"")
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_11)
    }
}

secrets {
    // Real values from gitignored local.properties; placeholders from the checked-in defaults.
    propertiesFileName = "local.properties"
    defaultPropertiesFileName = "secrets.defaults.properties"
}

dependencies {
    // Core / Compose
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.process)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.core)
    implementation(libs.androidx.material.icons.extended)

    // Maps + location + places
    implementation(libs.maps.compose)
    implementation(libs.play.services.maps)
    implementation(libs.play.services.location)
    implementation(libs.places)
    // Places SDK uses View-based Material Components styles (cornerFamily/cornerSize attrs).
    implementation(libs.google.material)

    // Image loading
    implementation(libs.coil.compose)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)

    // Coroutines
    implementation(libs.kotlinx.coroutines.android)

    // Network
    implementation(libs.retrofit)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging.interceptor)
    implementation(libs.retrofit.kotlinx.serialization.converter)
    implementation(libs.kotlinx.serialization.json)

    // DataStore
    implementation(libs.androidx.datastore.preferences)

    // Compose tooling (debug)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)

    // Unit tests
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.turbine)

    // Instrumented tests
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
}
