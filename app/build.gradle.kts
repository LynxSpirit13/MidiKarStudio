
import java.net.URL

// The compact TimGM6mb General MIDI SoundFont is downloaded at build time and bundled in the APK,
// so playback works offline after installation. Source is pinned to a specific commit.
val soundFontAsset = layout.projectDirectory.file("src/main/assets/default.sf2").asFile
val soundFontLicenseAsset = layout.projectDirectory.file("src/main/assets/TIMGM6MB-LICENSE.txt").asFile
val downloadDefaultSoundFont by tasks.registering {
    outputs.files(soundFontAsset, soundFontLicenseAsset)
    doLast {
        soundFontAsset.parentFile.mkdirs()
        if (!soundFontAsset.exists() || soundFontAsset.length() < 1_000_000L) {
            URL("https://raw.githubusercontent.com/craffel/pretty-midi/main/pretty_midi/TimGM6mb.sf2").openStream().use { input ->
                soundFontAsset.outputStream().use { output -> input.copyTo(output) }
            }
            check(soundFontAsset.length() > 1_000_000L) { "Default SoundFont download was incomplete." }
        }
        if (!soundFontLicenseAsset.exists()) {
            URL("https://raw.githubusercontent.com/deepin-community/timgm6mb-soundfont/master/debian/copyright").openStream().use { input ->
                soundFontLicenseAsset.outputStream().use { output -> input.copyTo(output) }
            }
        }
    }
}

tasks.named("preBuild").configure {
    dependsOn(downloadDefaultSoundFont)
}

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.midikarstudio.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.midikarstudio.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 2
        versionName = "0.2.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }
}

dependencies {
    implementation("dev.kotlinds:fluidsynth-kmp:1.1.1")
}
