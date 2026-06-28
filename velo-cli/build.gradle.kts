// velo-cli — the command-line tool: compiles and/or runs Velo programs and
// ships the default native classes (Terminal, Time, FileSystem, Http,
// Socket). The only module that links the compiler and the VM together.

plugins {
    application
}

dependencies {
    implementation(project(":velo-compiler"))
    implementation(project(":velo-vm"))

    // Parity gate: run the demo corpus on the clean-room velo-vm2 alongside the
    // legacy VM and assert identical output. Test-only — the CLI ships velo-vm.
    testImplementation(project(":velo-vm2"))
}

application {
    mainClass.set("MainKt")
}

tasks.named<JavaExec>("run") {
    // Resolve program paths relative to the repository root, where the
    // user invokes `./gradlew run --args="..."` from.
    workingDir = rootDir
}

// VM heap stress benchmark (see Bench.kt). Kernels live in <root>/bench.
tasks.register<JavaExec>("bench") {
    group = "verification"
    description = "Run the VM heap stress benchmark"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("BenchKt")
    workingDir = rootDir
    jvmArgs = listOf("-Xms512m", "-Xmx2g")
}
