import org.jetbrains.kotlin.gradle.plugin.mpp.apple.XCFramework

plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
    id("com.android.library")
}

kotlin {
    jvm()

    androidTarget {
        compilations.all {
            compileTaskProvider.configure {
                compilerOptions.jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
            }
        }
    }

    val xcf = XCFramework("Shared")
    listOf(iosX64(), iosArm64(), iosSimulatorArm64()).forEach { target ->
        target.binaries.framework {
            baseName = "Shared"
            isStatic = true
            xcf.add(this)
        }
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                // `api` (not `implementation`): PriceFeedClient's default `client` parameter
                // exposes HttpClient in its public signature, so consumers (androidApp) need it
                // on their compile classpath too, not just shared's own compile classpath.
                api("io.ktor:ktor-client-core:2.3.12")
                implementation("io.ktor:ktor-client-websockets:2.3.12")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.1")
                implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.6.1")
                // uint256 (ERC-20 balances, token IDs) overflows Long — needs real arbitrary
                // precision, not Double, and this is the standard KMP BigInteger implementation
                // rather than hand-rolling one.
                api("com.ionspin.kotlin:bignum:0.3.10")
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
                implementation("io.ktor:ktor-client-mock:2.3.12")
            }
        }
        val jvmMain by getting {
            dependencies {
                implementation("io.ktor:ktor-client-cio:2.3.12")
            }
        }
        val androidMain by getting {
            dependencies {
                implementation("io.ktor:ktor-client-okhttp:2.3.12")
                // PriceFeedController uses Dispatchers.Main, which needs this on the runtime
                // classpath. It was previously only present transitively (via androidApp's
                // Compose/AndroidX/Reown dependencies) — declared directly here so it doesn't
                // depend on what else a consuming app happens to pull in.
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
            }
        }
        val iosMain by creating {
            dependsOn(commonMain)
            dependencies {
                implementation("io.ktor:ktor-client-darwin:2.3.12")
            }
        }
        val iosX64Main by getting { dependsOn(iosMain) }
        val iosArm64Main by getting { dependsOn(iosMain) }
        val iosSimulatorArm64Main by getting { dependsOn(iosMain) }
    }
}

android {
    namespace = "dev.web3demo.realtimefeed"
    compileSdk = 34
    defaultConfig {
        minSdk = 24
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
