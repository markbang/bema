// Root build script: plugins are only declared so that the version catalog
// stays the single source of truth for plugin versions.
plugins {
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.androidKmpLibrary) apply false
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.kotlinSerialization) apply false
    alias(libs.plugins.composeCompiler) apply false
}

/**
 * Everything that can be verified on any machine without a device and without a
 * Mac: JVM host tests for the shared module plus the Android debug build. This
 * is also what the `android` CI job runs.
 *
 *   ./gradlew androidCheck                   # official structure, iOS targets on
 *   ./gradlew androidCheck -PskipIosTargets  # also skip the ~850 MB Kotlin/Native toolchain
 */
tasks.register("androidCheck") {
    group = "verification"
    description = "Runs the shared-module host tests and builds the Android debug APK."
    dependsOn(":app:sharedLogic:testAndroidHostTest", ":app:androidApp:assembleDebug")
}
