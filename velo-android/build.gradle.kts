import org.gradle.api.tasks.Copy

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "org.velo.android"
    compileSdk = 35

    defaultConfig {
        applicationId = "org.velo.android"
        minSdk = 23
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        viewBinding = true
    }
}

// Classpath used to compile the bundled .vel samples into .vbc at build time. The
// CLI host (`MainKt <in.vel> <out.vbc>`) registers the default native pool — exactly
// the set the samples are written against — and writes bytecode without running.
val veloCompiler: Configuration by configurations.creating

dependencies {
    veloCompiler(project(":velo-cli"))

    // The Velo VM — pure-JVM modules consumed as ordinary libraries. The compiler
    // is NOT shipped in the app: .vbc is produced at build time, loaded at runtime.
    implementation(project(":velo-vm"))
    implementation(project(":velo-core"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.google.material)

    testImplementation("junit:junit:4.13.2")
    // Host-only: lets the end-to-end test compile a .vel snippet against the app's
    // natives and run it. The compiler is NEVER shipped in the APK.
    testImplementation(project(":velo-compiler"))
}

// Compiles each sample's .vel into assets/samples/<id>/program.vbc and copies the
// authored meta.json next to it. The .vel sources live in the module root and never
// ship — only the resulting .vbc + meta.json land in the APK's assets, which the app
// enumerates at runtime to build the sample list.
val compileVeloSamples = tasks.register<CompileVeloSamplesTask>("compileVeloSamples") {
    samplesDir.set(layout.projectDirectory.dir("samples"))
    outputDir.set(layout.buildDirectory.dir("generated/veloSamples"))
    compilerClasspath.from(veloCompiler)
    mainClass.set("MainKt")
    javaLauncher.set(
        javaToolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(17)) },
    )
}

android.sourceSets.getByName("main").assets.srcDir(compileVeloSamples.flatMap { it.outputDir })

// AGP reads the assets dir as plain files and loses the task's builtBy, so wire the
// dependency explicitly for every variant's asset merge.
tasks.matching { it.name.startsWith("merge") && it.name.endsWith("Assets") }.configureEach {
    dependsOn(compileVeloSamples)
}

abstract class CompileVeloSamplesTask : DefaultTask() {
    @get:org.gradle.api.tasks.InputDirectory
    @get:org.gradle.api.tasks.PathSensitive(org.gradle.api.tasks.PathSensitivity.RELATIVE)
    abstract val samplesDir: org.gradle.api.file.DirectoryProperty

    @get:org.gradle.api.tasks.Classpath
    abstract val compilerClasspath: org.gradle.api.file.ConfigurableFileCollection

    @get:org.gradle.api.tasks.Input
    abstract val mainClass: org.gradle.api.provider.Property<String>

    @get:org.gradle.api.tasks.Nested
    abstract val javaLauncher: org.gradle.api.provider.Property<org.gradle.jvm.toolchain.JavaLauncher>

    @get:org.gradle.api.tasks.OutputDirectory
    abstract val outputDir: org.gradle.api.file.DirectoryProperty

    @get:javax.inject.Inject
    abstract val execOps: org.gradle.process.ExecOperations

    @get:javax.inject.Inject
    abstract val fsOps: org.gradle.api.file.FileSystemOperations

    @org.gradle.api.tasks.TaskAction
    fun generate() {
        val srcRoot = samplesDir.get().asFile
        val outRoot = outputDir.get().asFile
        fsOps.delete { delete(outRoot) }
        val samplesOut = outRoot.resolve("samples").apply { mkdirs() }

        val sampleDirs = srcRoot.listFiles()
            ?.filter { it.isDirectory && it.resolve("meta.json").exists() }
            ?.sortedBy { it.name }
            .orEmpty()
        require(sampleDirs.isNotEmpty()) { "no samples (samples/<id>/meta.json) under $srcRoot" }

        for (dir in sampleDirs) {
            val id = dir.name
            val vel = dir.listFiles()?.firstOrNull { it.isFile && it.extension == "vel" }
                ?: error("sample '$id' has no .vel source")
            val sampleOut = samplesOut.resolve(id).apply { mkdirs() }
            val vbc = sampleOut.resolve("program.vbc")

            execOps.javaexec {
                executable = javaLauncher.get().executablePath.asFile.absolutePath
                classpath = compilerClasspath
                mainClass.set(this@CompileVeloSamplesTask.mainClass)
                args(vel.absolutePath, vbc.absolutePath)
            }
            require(vbc.exists()) { "compile produced no .vbc for sample '$id'" }

            dir.resolve("meta.json").copyTo(sampleOut.resolve("meta.json"), overwrite = true)
            logger.lifecycle("velo sample: $id -> ${vbc.length()} bytes")
        }
    }
}
