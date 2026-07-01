pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "Darrbi"
include(":app")
// Compose Multiplatform module (Android + iOS) for the cross-platform migration. Lives alongside :app,
// which remains the production Android app until features are migrated in.
// :composeApp is the shared library (UI + iOS framework); :androidApp is the thin Android host.
include(":composeApp")
include(":androidApp")
 