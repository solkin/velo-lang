package integration

import core.NativeMappingException
import core.NativeRegistry
import core.VmType
import vm.VM
import vm.VMContext
import vm.VMExecutor
import vm.VMProfiler
import vm.VeloProgram
import vm.VeloRuntime

/**
 * Host classes for native-binding tests — plain Kotlin, no annotations,
 * exactly what an embedding host would write.
 */

/** Constructor with arguments + native values passing between methods. */
class Box(label: String) {
    private var content = label

    fun wrap(prefix: String): Box = Box(prefix + content)

    fun read(): String = content

    fun fill(other: Box) {
        content = other.read()
    }
}

/** Callback declared as a Kotlin function type — full signature visible to Velo. */
class KotlinCallbacks {
    fun each(cb: (Int) -> Unit) {
        cb(1)
        cb(2)
        cb(3)
    }
}

/** Velo has no overloads — registration must reject this class. */
class OverloadedHost {
    fun f(a: Int): Int = a
    fun f(a: String): String = a
}

/** Exactly one public constructor is required. */
class TwoCtorsHost() {
    constructor(a: Int) : this()

    fun ok(): Int = 1
}
