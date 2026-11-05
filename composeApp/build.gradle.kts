import java.util.Properties
import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("multiplatform")
    kotlin("plugin.compose")
    kotlin("plugin.serialization")
    id("org.jetbrains.compose")
    id("com.android.application")
}

kotlin {
    androidTarget {
        compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
    }
    jvm("desktop")

    // The web version (Kotlin/Wasm), published on GitHub Pages; see .github/workflows/build.yml.
    @OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)
    wasmJs {
        browser {
            commonWebpackConfig { outputFileName = "composeApp.js" }
        }
        binaries.executable()
    }

    sourceSets {
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.materialIconsExtended)
            implementation(compose.components.resources)
            implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.1")
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
        }
        wasmJsMain.dependencies {
            implementation("org.jetbrains.kotlinx:kotlinx-browser:0.3")
        }
        androidMain.dependencies {
            implementation("androidx.activity:activity-compose:1.10.1")
        }
        val desktopTest by getting {
            dependencies { implementation(kotlin("test")) }
        }
        val desktopMain by getting {
            dependencies {
                implementation(compose.desktop.currentOs)
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.10.2")
                // Pure-Java MP3 decoder (LGPL) used to play EveryAyah recitations on desktop.
                implementation("com.googlecode.soundlibs:jlayer:1.0.1.4")
            }
        }
    }
}

compose.resources {
    packageOfResClass = "app.iqra.resources"
    publicResClass = false
}

// Release signing key, kept out of git: copy keystore.properties.example to keystore.properties
// and fill it in (see docs/RELEASING.md). Without it, release builds are simply unsigned.
val keystoreProperties = rootProject.file("keystore.properties").takeIf { it.exists() }?.let { file ->
    Properties().apply { file.inputStream().use { load(it) } }
}

android {
    namespace = "app.iqra"
    compileSdk = 35
    defaultConfig {
        applicationId = "app.alifup"
        minSdk = 26
        targetSdk = 35
        versionCode = 3
        versionName = "1.0.0"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    val props = keystoreProperties
    if (props != null) {
        signingConfigs {
            create("release") {
                // A relative path is relative to the project folder; CI writes an absolute one.
                storeFile = rootProject.file(props.getProperty("storeFile"))
                storePassword = props.getProperty("storePassword")
                keyAlias = props.getProperty("keyAlias")
                keyPassword = props.getProperty("keyPassword")
            }
        }
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            if (props != null) signingConfig = signingConfigs.getByName("release")
        }
    }
}

compose.desktop {
    application {
        mainClass = "app.iqra.MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Msi, TargetFormat.Exe)
            packageName = "AlifUp"
            packageVersion = "1.0.0"
            description = "Learn to read and memorise the Quran"
            vendor = "AlifUp"
            windows {
                iconFile.set(project.file("icon.ico"))
                menuGroup = "AlifUp"
                shortcut = true
                // Keeps upgrades installing over the previous version instead of side by side.
                upgradeUuid = "042d94df-165c-4b60-8b29-7decafa8f176"
            }
            linux { iconFile.set(project.file("icon.png")) }
        }
    }
}
