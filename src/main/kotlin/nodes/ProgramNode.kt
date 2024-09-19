package nodes

import CompilerContext
import Environment
import vm2.operations.Drop
import vm2.operations.Ext
import vm2.operations.Free

data class ProgramNode(
    val prog: List<Node>,
) : Node() {
    override fun evaluate(env: Environment<Type<*>>): Type<*> {
        val scope = env.extend()
        var v: Type<*> = BoolType(false)
        prog.forEach { v = it.evaluate(scope) }
        return v
    }

    override fun compile(ctx: CompilerContext): VMType {
        ctx.add(Ext())
        var type: VMType = VMVoid
        prog.forEachIndexed { index, node ->
            type = node.compile(ctx)
            if (type != VMVoid && index != prog.size-1) {
                ctx.add(Drop())
            }
        }
        ctx.add(Free())
        return type
    }
}