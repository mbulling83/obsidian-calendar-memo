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
        // Onyx's SDK (onyxsdk-device/pen/base) is only published here,
        // not on Maven Central or Google's repo. Used by U7 for the
        // built-in MyScript handwriting recognition AIDL service.
        maven {
            url = uri("https://repo.boox.com/repository/maven-public/")
        }
    }
}

rootProject.name = "boxmemo"
include(":app")
