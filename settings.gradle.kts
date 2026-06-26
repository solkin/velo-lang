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

rootProject.name = "velo-lang"

include(":velo-core")
include(":velo-compiler")
include(":velo-vm")
include(":velo-cli")
include(":velo-android")
include(":velo-android-tools")
