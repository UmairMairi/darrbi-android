import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Compose Multiplatform shared LIBRARY: shares Compose UI + logic across Android and iOS.
// AGP 9 forbids com.android.application/library alongside the KMP plugin, so the Android side uses the
// new built-in `androidLibrary` DSL. The runnable Android app lives in :androidApp (depends on this);
// the iOS side exports a static "ComposeApp" framework consumed by the Xcode iosApp project.
plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kmp.library)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.jetbrains.compose)
}

kotlin {
    androidLibrary {
        namespace = "com.mytm.darrbi.shared"
        compileSdk = 36
        minSdk = 26

        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }

    listOf(
        iosX64(),
        iosArm64(),
        iosSimulatorArm64(),
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "ComposeApp"
            isStatic = true
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.materialIconsExtended)
            implementation(compose.ui)
            implementation(compose.components.resources)
            implementation(libs.kotlinx.coroutines.core)
        }
        androidMain.dependencies {
            implementation(libs.androidx.activity.compose)
            implementation(libs.kotlinx.coroutines.android)
        }
    }
}

// Compose resources (fonts, strings, drawables) generate into this package's `Res` class.
compose.resources {
    publicResClass = true
    packageOfResClass = "com.mytm.darrbi.shared.resources"
    generateResClass = always
}
