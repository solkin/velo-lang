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
include(":velo-vm2")
include(":velo-vm3")
include(":velo-cli")
include(":velo-android")
include(":velo-android-tools")
