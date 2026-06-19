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

/**
 * Host counterpart of a Velo `data class Point(int x, int y)` — registered via
 * `registerData` and marshalled by value across the boundary. A Kotlin data
 * class satisfies the binding contract for free (single constructor in field
 * order; `getX()`/`getY()` accessors).
 */
data class NativePoint(val x: Int, val y: Int)

/** Host counterpart of a nested Velo `data class Segment(Point a, Point b)`. */
data class NativeSegment(val a: NativePoint, val b: NativePoint)

/** Host API that takes and returns data classes by value. */
class Geometry {
    fun translate(p: NativePoint, dx: Int, dy: Int): NativePoint = NativePoint(p.x + dx, p.y + dy)

    fun origin(): NativePoint = NativePoint(0, 0)

    fun describe(p: NativePoint): String = "(${p.x}, ${p.y})"

    fun start(s: NativeSegment): NativePoint = s.a
}
