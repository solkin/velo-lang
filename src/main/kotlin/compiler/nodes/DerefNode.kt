package compiler.nodes

import compiler.Context
import vm.operations.PtrLoad
import vm.operations.PtrStore

/**
 * AST Node for explicit pointer dereference: `*ptr`
 * This is an alternative syntax to `ptr.*` or `ptr.val`
 */
data class DerefNode(
    val pointer: Node,
) : Node(), AssignableNode {
    override fun compile(ctx: Context): Type {
        val ptrType = pointer.compile(ctx)
        if (ptrType !is PtrType) {
            throw IllegalArgumentException("Cannot dereference non-pointer type: ${ptrType.log()}")
        }
        ctx.add(PtrLoad())
        return ptrType.derived
    }

    override fun compileAssignment(type: Type, ctx: Context) {
        val ptrType = pointer.compile(ctx)
        if (ptrType !is PtrType) {
            throw IllegalArgumentException("Cannot assign through non-pointer type: ${ptrType.log()}")
        }
        if (!ptrType.derived.sameAs(type) && !ptrType.derived.sameAs(AnyType)) {
            throw IllegalArgumentException("Type mismatch in pointer assignment: expected ${ptrType.derived.log()}, got ${type.log()}")
        }
        ctx.add(PtrStore())
    }
}

