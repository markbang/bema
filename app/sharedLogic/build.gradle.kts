import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.apple.XCFramework

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    // AGP 9+ forbids com.android.library / com.android.application in a KMP module.
    alias(libs.plugins.androidKmpLibrary)
}

kotlin {
    // Android target. Sources live in src/androidMain (not src/main) with the
    // Android-KMP library plugin. The block is `android` inside `kotlin` — the
    // older `androidLibrary` name is deprecated.
    android {
        namespace = "dev.bema.shared"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()
        compilerOptions {
            jvmTarget = JvmTarget.JVM_17
        }
        // Opt in to host-side unit tests (src/androidHostTest).
        withHostTest {}
    }

    // Apple targets. The framework is consumed by the UIKit app in app/iosApp
    // through direct integration (embedAndSignAppleFrameworkForXcode); the
    // XCFramework export below is what you would publish for remote
    // integration (SwiftPM/CocoaPods) if you ever split the repos.
    //
    // Pass -PskipIosTargets (or -PskipIosTargets=true) to drop them: on
    // Linux/low-RAM machines this avoids the ~850 MB Kotlin/Native toolchain
    // and three klib compilations on every build. Local-development switch
    // only — never set it in CI, in gradle.properties, or anywhere a release is
    // built, otherwise the iOS app has no framework to link against.
    // Note: Gradle turns a valueless `-PskipIosTargets` into an empty string,
    // which `toBoolean()` would read as false, hence the explicit emptiness check.
    val skipIosTargets = providers.gradleProperty("skipIosTargets").orNull
        ?.let { it.isEmpty() || it.toBoolean() }
        ?: false

    if (!skipIosTargets) {
        val xcf = XCFramework("SharedLogic")
        listOf(iosX64(), iosArm64(), iosSimulatorArm64()).forEach { iosTarget ->
            iosTarget.binaries.framework {
                baseName = "SharedLogic"
                isStatic = true
                xcf.add(this)
            }
        }
    }

    sourceSets {
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}
