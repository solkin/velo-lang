package compiler.nodes

import compiler.Context
import vm.operations.Call
import vm.operations.MakePtr
import vm.operations.Move
import vm.operations.Ret

data class ScopeNode(
    val child: Node,
) : Node() {
    override fun compile(ctx: Context): Type {
        return ctx.wrapScope { child.compile(it) }
    }
}

fun Context.wrapScope(compile: (Context) -> Type): Type {
    val scopeOps = extend()
    val type = compile(scopeOps)
    // Add return to scope body
    scopeOps.add(Ret())

    // Prepare scope body address
    add(MakePtr(2))
    // Skip scope body
    add(Move(scopeOps.size()))
    // Add scope operations to real context
    merge(scopeOps)
    // Call scope body
    add(Call(args = 0))

    return type
}
