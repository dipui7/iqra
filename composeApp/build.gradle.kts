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

android {
    namespace = "app.iqra"
    compileSdk = 35
    defaultConfig {
        applicationId = "app.alifup"
        minSdk = 26
        targetSdk = 35
        versionCode = 2
        versionName = "0.2.0"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildTypes {
        getByName("release") { isMinifyEnabled = false }
    }
}

compose.desktop {
    application {
        mainClass = "app.iqra.MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Msi, TargetFormat.Exe)
            packageName = "AlifUp"
            packageVersion = "0.2.0"
            description = "Learn to read and memorise the Quran"
            vendor = "Iqra"
            windows {
                iconFile.set(project.file("icon.ico"))
                menuGroup = "Iqra"
                shortcut = true
                // Keeps upgrades installing over the previous version instead of side by side.
                upgradeUuid = "042d94df-165c-4b60-8b29-7decafa8f176"
            }
            linux { iconFile.set(project.file("icon.png")) }
        }
    }
}
