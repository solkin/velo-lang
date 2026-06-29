package core

/**
 * A complete compiled Velo program: the bytecode frames plus the native
 * pool — the constant-pool-style table of every native entry point the
 * code references. `.vbc` files serialize exactly this (see [Bytecode]);
 * the VM links the pool against the host's [NativeRegistry] before
 * execution starts.
 */
data class SerializedProgram(
    val natives: List<NativeRef>,
    val frames: List<SerializedFrame>,
    val dataClasses: List<DataClassInfo> = emptyList(),
    val classMethods: List<ClassMethodsInfo> = emptyList(),
)
