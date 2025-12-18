package compiler.nodes

import compiler.Context
import vm.VmType
import vm.operations.PtrLoad
import vm.operations.PtrNew
import vm.operations.PtrStore
import vm.operations.Push
import vm.records.PtrRecord

/**
 * AST Node for the `null` literal.
 * Can be assigned to any pointer type.
 */
object NullNode : Node() {
    override fun compile(ctx: Context): Type {
        ctx.add(Push(PtrRecord.Null))
        return NullType
    }
}

/**
 * Special type for null literal that is compatible with any pointer type.
 */
object NullType : Type {
    override fun sameAs(type: Type): Boolean {
        // null is compatible with any pointer type
        return type is PtrType || type is NullType || type is AnyType
    }

    override fun default(ctx: Context) {
        ctx.add(Push(PtrRecord.Null))
    }

    override fun prop(name: String): Prop? = null

    override fun log() = "null"

    override fun vmType() = vm.VmType.Any

    override fun name() = "null"
}

/**
 * AST Node for creating a new pointer: `new ptr[T](value)` or `new ptr[T]()`
 */
data class PtrNode(
    val initialValue: Node?,
    val derivedType: Type,
) : Node() {
    override fun compile(ctx: Context): Type {
        if (initialValue != null) {
            val valueType = initialValue.compile(ctx)
            if (!derivedType.sameAs(valueType) && !derivedType.sameAs(AnyType)) {
                throw IllegalArgumentException("Pointer type mismatch: expected ${derivedType.log()}, got ${valueType.log()}")
            }
        } else {
            // Push null pointer record for uninitialized pointers
            ctx.add(Push(PtrRecord.Null))
            return PtrType(derivedType)
        }
        ctx.add(PtrNew())
        return PtrType(derivedType)
    }
}

/**
 * Type representing a pointer to another type: `ptr[T]`
 */
data class PtrType(val derived: Type) : Type {
    override fun sameAs(type: Type): Boolean {
        // Accept null type for any pointer
        if (type is NullType) return true
        return type is PtrType && (type.derived.sameAs(derived) || derived.sameAs(AnyType) || type.derived.sameAs(AnyType))
    }

    override fun default(ctx: Context) {
        ctx.add(Push(PtrRecord.Null))
    }

    override fun prop(name: String): Prop? {
        return when (name) {
            "val", "*" -> PtrDerefProp(derived)
            else -> null
        }
    }

    override fun log() = "ptr[${derived.log()}]"
    override fun vmType() = VmType.Ptr(derived.vmType())
    override fun name() = "ptr"
}

/**
 * Property for dereferencing a pointer: `p.val` or `p.*`
 */
data class PtrDerefProp(val targetType: Type) : AssignableProp {
    override fun compile(type: Type, args: List<Type>, ctx: Context): Type {
        ctx.add(PtrLoad())
        return targetType
    }

    override fun compileAssignment(parentType: Type, assignType: Type, args: List<Type>, ctx: Context) {
        ctx.add(PtrStore())
    }
}

