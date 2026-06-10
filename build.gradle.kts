// Top-level build file. Declare every plugin used by subprojects with `apply false`
// (required on AGP 9 so modules don't fail with "plugin already on the classpath").
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.secrets) apply false
}
