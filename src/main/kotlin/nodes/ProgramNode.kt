package nodes

import CompilerContext
import Environment
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

    override fun compile(ctx: CompilerContext): DataType {
        ctx.add(Ext())
        var type = DataType.VOID
        prog.forEach { type = it.compile(ctx) }
        ctx.add(Free())
        return type
    }
}