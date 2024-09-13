package nodes

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

    override fun compile(ops: MutableList<Operation>) {
        parent.compile(ops)
        args.orEmpty().reversed().forEach { it.compile(ops) }
//        parent.property(name, ops)
        when(name) {
            "str" -> ops.add(SubStr())
            "len" -> ops.add(StrLen())

            "subSlice" -> ops.add(SubSlice())
            "size" -> ops.add(SliceLen())
            "map" -> {
                val func = 2
                ops.add(Def(func))

                ops.add(Dup())
                ops.add(SliceLen())
                val size = 1
                ops.add(Def(size))

                ops.add(Push(0))
                val i = 3
                ops.add(Def(i))

                val list = 4
                ops.add(Def(list))

                val condOps: MutableList<Operation> = ArrayList()
                with(condOps) {
                    add(Get(i))
                    add(Get(size))
                    add(Less())
                }

                val exprOps: MutableList<Operation> = ArrayList()
                with(exprOps) {
                    // item
                    add(Get(list))
                    add(Get(i))
                    add(Index())
                    // index
                    add(Get(i))
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
                exprOps.add(Move(-(exprOps.size + condOps.size + 2))) // +2 because to move and if is not included

                ops.addAll(condOps)
                ops.add(If(exprOps.size))
                ops.addAll(exprOps)

                ops.add(Get(size))
                ops.add(Slice())
            }
            else -> throw IllegalArgumentException("Property $name is not supported")
        }
    }
}