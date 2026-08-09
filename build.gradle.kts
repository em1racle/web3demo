plugins {
    kotlin("multiplatform") version "2.0.20" apply false
    kotlin("android") version "2.0.20" apply false
    kotlin("plugin.serialization") version "2.0.20" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.20" apply false
    id("com.android.library") version "8.5.2" apply false
    id("com.android.application") version "8.5.2" apply false
    id("org.jlleitschuh.gradle.ktlint") version "12.1.1" apply false
}

subprojects {
    apply(plugin = "org.jlleitschuh.gradle.ktlint")
}
