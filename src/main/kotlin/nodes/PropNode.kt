package nodes

import CompilerContext
import Environment
import vm2.Operation
import vm2.operations.*
import vm2.operations.Set

data class PropNode(
    val name: String,
    val args: List<Node>?,
    val parent: Node
) : Node() {
    override fun evaluate(env: Environment<Type<*>>): Type<*> {
        val v = parent.evaluate(env)
        val a = args?.map { it.evaluate(env) }
        return v.property(name, a)
    }

    override fun compile(ctx: CompilerContext) {
        parent.compile(ctx)
        args.orEmpty().reversed().forEach { it.compile(ctx) }
//        parent.property(name, ctx)
        when(name) {
            "str" -> ctx.add(SubStr())
            "len" -> ctx.add(StrLen())

            "subSlice" -> ctx.add(SubSlice())
            "size" -> ctx.add(SliceLen())
            "map" -> {
                val func = 2
                ctx.add(Def(func))

                ctx.add(Dup())
                ctx.add(SliceLen())
                val size = 1
                ctx.add(Def(size))

                ctx.add(Push(0))
                val i = 3
                ctx.add(Def(i))

                val list = 4
                ctx.add(Def(list))

                val condCtx: MutableList<Operation> = ArrayList()
                with(condCtx) {
                    add(Get(i))
                    add(Get(size))
                    add(Less())
                }

                val exprCtx: MutableList<Operation> = ArrayList()
                with(exprCtx) {
                    // index
                    add(Get(i))
                    // item
                    add(Get(list))
                    add(Get(i))
                    add(Index())
                    // func
                    add(Get(func))
                    // call func
                    add(Call())
                    // increment i
                    add(Get(i))
                    add(Push(1))
                    add(Plus())
                    add(Set(i))
                }
                exprCtx.add(Move(-(exprCtx.size + condCtx.size + 2))) // +2 because to move and if is not included

                ctx.addAll(condCtx)
                ctx.add(If(exprCtx.size))
                ctx.addAll(exprCtx)

                ctx.add(Get(size))
                ctx.add(Slice())
            }
            else -> throw IllegalArgumentException("Property $name is not supported")
        }
    }
}