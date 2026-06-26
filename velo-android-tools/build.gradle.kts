// Build-time tooling for :velo-android — NOT shipped in the app and NOT part of the CLI.
// Provides the UI-aware sample compiler (CompileMainKt) that the `compileVeloSamples`
// task runs: the standard natives from :velo-cli plus pure-JVM Ui/View signature stubs,
// so `.vel` programs that drive Material3 screens type-check without an Android SDK.
// The kotlin-jvm plugin and toolchain come from the root `subprojects` block.
dependencies {
    implementation(project(":velo-cli"))
    implementation(project(":velo-compiler"))
    implementation(project(":velo-core"))
}
