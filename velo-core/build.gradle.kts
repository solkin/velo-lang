// velo-core — the contract shared by the compiler and the VM:
// the Op instruction set, VmType, the native-interop descriptors and
// registry, and the .vbc bytecode format. No execution engine here.

dependencies {
    testImplementation(kotlin("reflect")) // Op.sealedSubclasses in the round-trip test
}
