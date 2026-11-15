plugins {
    id("com.android.application")
}

android {
    namespace = "io.github.kamaje122.calwidget"
    compileSdk = 36

    defaultConfig {
        applicationId = "io.github.kamaje122.calwidget"
        minSdk = 31
        targetSdk = 36
        versionCode = 5
        versionName = "1.4"
    }

    // 固定的側載用簽章：讓每次（本機 / GitHub Actions）建出來的 APK 都能直接覆蓋更新。
    signingConfigs {
        create("sideload") {
            storeFile = rootProject.file("keystore/sideload.jks")
            storePassword = "android"
            keyAlias = "sideload"
            keyPassword = "android"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("sideload")
        }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("sideload")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
