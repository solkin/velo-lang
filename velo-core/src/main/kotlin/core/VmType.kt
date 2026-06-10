package core

/**
 * The Velo type system as the bytecode and the native interop see it.
 *
 * Pure data: used by the compiler for native signature checking, serialized
 * into `.vbc` (native pool entries and `NativeCall` call-site types), and
 * consulted by the VM when converting values across the Velo ⇄ JVM boundary.
 * [toString] renders the Velo-source spelling — these strings appear in
 * compiler and linker diagnostics.
 */
sealed class VmType {

    object Void : VmType() {
        override fun toString() = "void"
    }

    object Any : VmType() {
        override fun toString() = "any"
    }

    object Byte : VmType() {
        override fun toString() = "byte"
    }

    object Int : VmType() {
        override fun toString() = "int"
    }

    object Float : VmType() {
        override fun toString() = "float"
    }

    object Str : VmType() {
        override fun toString() = "str"
    }

    object Bool : VmType() {
        override fun toString() = "bool"
    }

    data class Tuple(val elementTypes: List<VmType> = emptyList()) : VmType() {
        override fun toString() = "tuple[${elementTypes.joinToString()}]"
    }

    data class Array(val elementType: VmType = Any) : VmType() {
        override fun toString() = "array[$elementType]"
    }

    /** A registered native class, referenced by its Velo name. */
    data class Class(val name: String) : VmType() {
        override fun toString() = name
    }

    /**
     * Function value. [args]/[ret] carry the declared Velo signature when
     * known (`func[(str, int) void]`); the loose `func[T]` form has
     * `args == null`. Native methods receive function values as
     * [VeloFunction] parameters — the signature is what lets the host side
     * validate and convert invocation arguments without the compiler around.
     */
    data class Func(val args: List<VmType>? = null, val ret: VmType? = null) : VmType() {
        override fun toString() =
            "func[(${args?.joinToString() ?: "?"}) ${ret ?: "?"}]"
    }

    data class Ptr(val derived: VmType) : VmType() {
        override fun toString() = "ptr[$derived]"
    }
}
