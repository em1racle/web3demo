pluginManagement {
    repositories {
        google()
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        // Reown AppKit pulls in a handful of transitive deps (Scarlet fork, kethereum, a QR
        // generator) that are only published here, not on Maven Central.
        maven("https://jitpack.io")
    }
}

rootProject.name = "web3-mobile-demo"

include(":shared")
include(":androidApp")
