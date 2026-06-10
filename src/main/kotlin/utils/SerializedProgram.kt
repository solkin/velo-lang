package utils

import vm.NativeRef

/**
 * A complete compiled Velo program: the bytecode frames plus the native
 * pool — the constant-pool-style table of every native entry point the
 * code references. `.vbc` files serialize exactly this; [vm.VM.load] links
 * the pool against the host's [vm.NativeRegistry] before execution starts.
 */
data class SerializedProgram(
    val natives: List<NativeRef>,
    val frames: List<SerializedFrame>,
)
