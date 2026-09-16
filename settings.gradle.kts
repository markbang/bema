@file:Suppress("UnstableApiUsage")

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

dependencyResolutionManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
    }
}

rootProject.name = "bema"

// Official Kotlin Multiplatform "new default" structure (2026):
// every app entry point is its own module, shared code lives in sharedLogic.
//   app/androidApp   -> Android application (AGP 9 requires a separate entry point module)
//   app/sharedLogic  -> KMP shared business logic (androidLibrary + iOS targets)
//   app/iosApp       -> Xcode project (UIKit), consumes the SharedLogic framework
include(":app:androidApp")
include(":app:sharedLogic")
