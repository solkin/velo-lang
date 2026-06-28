// velo-vm2 — a clean-room reimplementation of the Velo virtual machine,
// written from the .vbc bytecode spec (velo-core) and verified against the
// golden tests. Depends only on the core contract; the front-end compiler is
// pulled in for tests so golden .vel sources can be compiled and executed.

dependencies {
    api(project(":velo-core"))

    testImplementation(project(":velo-compiler"))
}
