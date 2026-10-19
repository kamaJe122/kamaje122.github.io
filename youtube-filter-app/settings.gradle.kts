pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "YtFilter"
include(":core")

// The Android module needs an SDK; skip it where none is installed so :core still builds and tests.
val localSdk = file("local.properties").takeIf { it.exists() }?.readText()?.contains("sdk.dir") == true
if (localSdk || System.getenv("ANDROID_HOME") != null || System.getenv("ANDROID_SDK_ROOT") != null) {
    include(":app")
}
