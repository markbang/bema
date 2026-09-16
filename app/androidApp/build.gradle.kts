import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    // Kotlin support is built into AGP 9, so org.jetbrains.kotlin.android must
    // NOT be applied here.
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeCompiler)
}

// version.txt is the single source of truth for the app version; release-please
// bumps it when a release PR is merged (.github/workflows/release-please.yml).
val appVersion = providers
    .fileContents(rootProject.layout.projectDirectory.file("version.txt"))
    .asText.get().trim()

/** Play requires a monotonically increasing version code: 1.2.3 -> 1002003. */
fun versionCodeOf(version: String): Int {
    val parts = version.substringBefore("-").split(".").map { it.toIntOrNull() ?: 0 }
    return parts.getOrElse(0) { 0 } * 1_000_000 +
        parts.getOrElse(1) { 0 } * 1_000 +
        parts.getOrElse(2) { 0 }
}

// Release signing is configured only when a keystore is available (CI secrets,
// see .github/workflows/release.yml). Without it `assembleRelease` produces an
// unsigned APK; the .aab can still be uploaded to Play, which re-signs it.
val keystorePath = providers.environmentVariable("ANDROID_KEYSTORE_FILE").orNull
val keystorePassword = providers.environmentVariable("ANDROID_KEYSTORE_PASSWORD").orNull
val keyAliasName = providers.environmentVariable("ANDROID_KEY_ALIAS").orNull
val keyPasswordValue = providers.environmentVariable("ANDROID_KEY_PASSWORD").orNull
val hasReleaseSigning = listOf(keystorePath, keystorePassword, keyAliasName, keyPasswordValue)
    .all { !it.isNullOrBlank() }

android {
    namespace = "dev.bema.android"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "dev.bema.android"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = versionCodeOf(appVersion)
        versionName = appVersion
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = file(keystorePath!!)
                storePassword = keystorePassword
                this.keyAlias = keyAliasName
                keyPassword = keyPasswordValue
            }
        }
    }

    buildTypes {
        getByName("release") {
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a", "x86", "x86_64")
            isUniversalApk = true
        }
    }

    buildFeatures {
        compose = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
    }
}

dependencies {
    implementation(project(":app:sharedLogic"))

    // Compose
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.activity)

    // Coil
    implementation(libs.coil.compose)
}
