package nodes

import CompilerContext
import Environment
import vm2.Operation
import vm2.operations.Call
import vm2.operations.Def
import vm2.operations.Dup
import vm2.operations.Get
import vm2.operations.If
import vm2.operations.Index
import vm2.operations.Less
import vm2.operations.Move
import vm2.operations.Plus
import vm2.operations.Push
import vm2.operations.Set
import vm2.operations.Slice
import vm2.operations.SliceLen
import vm2.operations.StrLen
import vm2.operations.SubSlice
import vm2.operations.SubStr

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

    override fun compile(ctx: CompilerContext): VMType {
        parent.compile(ctx)
        args.orEmpty().reversed().forEach { it.compile(ctx) }
        /*when(name) {
            "str" -> ctx.add(SubStr())
            "len" -> ctx.add(StrLen())

            "subSlice" -> ctx.add(SubSlice())
            "size" -> ctx.add(SliceLen())
            "map" -> {
                val func = ctx.varIndex("_func")
                ctx.add(Def(func))

                ctx.add(Dup())
                ctx.add(SliceLen())
                val size = ctx.varIndex("_size")
                ctx.add(Def(size))

                ctx.add(Push(0))
                val i = ctx.varIndex("_i")
                ctx.add(Def(i))

                val list = ctx.varIndex("_list")
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
        }*/
        throw IllegalArgumentException("Property $name is not supported")
    }
}