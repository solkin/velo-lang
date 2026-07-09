plugins {
    kotlin("jvm") version "1.9.24" apply false
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
}

// The Android demo (:velo-android) brings its own AGP/Kotlin-Android plugins and
// SDK toolchain; only the pure-JVM modules get the shared kotlin-jvm config below.
configure(subprojects.filter { it.name != "velo-android" }) {
    apply(plugin = "org.jetbrains.kotlin.jvm")

    group = "org.velo.lang"
    version = "1.0-SNAPSHOT"

    dependencies {
        "testImplementation"(kotlin("test"))
    }

    tasks.withType<Test> {
        useJUnitPlatform()
        // Forward -Dvelo.* flags (e.g. fuzz seed/count, gc threshold) to the test JVM.
        System.getProperties().forEach { k, v ->
            if (k is String && k.startsWith("velo.")) systemProperty(k, v.toString())
        }
    }

    extensions.configure<org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension> {
        jvmToolchain(11)
    }
}
