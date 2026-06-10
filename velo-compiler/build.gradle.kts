// velo-compiler — parses .vel sources and compiles them to a
// SerializedProgram (bytecode + native pool). Depends only on the core
// contract; no VM on the classpath.

dependencies {
    api(project(":velo-core"))
}
