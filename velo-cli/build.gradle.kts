// velo-cli — the command-line tool: compiles and/or runs Velo programs and
// ships the default native classes (Terminal, Time, FileSystem, Http,
// Socket). The only module that links the compiler and the VM together.

plugins {
    application
}

dependencies {
    implementation(project(":velo-compiler"))
    implementation(project(":velo-vm"))
}

application {
    mainClass.set("MainKt")
}

tasks.named<JavaExec>("run") {
    // Resolve program paths relative to the repository root, where the
    // user invokes `./gradlew run --args="..."` from.
    workingDir = rootDir
}
