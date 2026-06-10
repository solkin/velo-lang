package compiler.nodes

import core.Op

import compiler.Context

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
    scopeOps.add(Op.Ret)

    // Prepare scope body address
    add(Op.Frame(num = scopeOps.frame.num))
    // Add scope operations to real context
    merge(scopeOps)
    // Call scope body
    add(Op.Call(args = 0))

    return type
}
