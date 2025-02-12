package compiler.nodes

import compiler.Context
import vm.operations.Call
import vm.operations.Move
import vm.operations.Pc
import vm.operations.Plus
import vm.operations.Push
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
    add(Pc())
    add(Push(value = 4))
    add(Plus())
    // Skip scope body
    add(Move(scopeOps.size()))
    // Add scope operations to real context
    merge(scopeOps)
    // Call scope body
    add(Call(args = 0))

    return type
}
