import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    // Kotlin support is built into AGP 9, so org.jetbrains.kotlin.android must
    // NOT be applied here.
    alias(libs.plugins.androidApplication)
}

android {
    namespace = "dev.bema.android"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "dev.bema.android"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 1
        versionName = "0.1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
    }
}

dependencies {
    implementation(project(":app:sharedLogic"))
}
