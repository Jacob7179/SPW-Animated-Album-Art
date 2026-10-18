pluginManagement {
    repositories {
        maven("https://jitpack.io")
        gradlePluginPortal()
    }
    plugins {
        id("org.jetbrains.kotlin.kapt") version "2.4.20"
    }
    resolutionStrategy {
        eachPlugin {
            if (requested.id.id == "com.xuncorp.spw.workshop") {
                useModule(
                    "com.github.Moriafly.spw-workshop-api:spw-workshop-gradle-plugin:${requested.version}"
                )
            }
        }
    }
}

// Java 25 is selected from a locally installed JDK by build.bat.
// Foojay resolver removed to avoid IBM_SEMERU vendor compatibility errors.

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven("https://jitpack.io")
    }
}

rootProject.name = "example-plugin"
