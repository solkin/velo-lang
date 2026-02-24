package compiler.nodes

import compiler.Context
import vm.operations.Load
import vm.operations.Store
import java.util.concurrent.atomic.AtomicInteger

private val opTempCounter = AtomicInteger(0)

data class IndexNode(
    val list: Node,
    val index: Node,
) : Node(), AssignableNode {
    override fun compile(ctx: Context): Type {
        val type = list.compile(ctx)
        if (type is ClassType) {
            val indexType = index.compile(ctx)
            val prop = ClassElementProp("op@[]")
            try {
                return prop.compile(type, args = listOf(indexType), ctx)
            } catch (e: IllegalArgumentException) {
                throw IllegalArgumentException("Operator '[]' is not defined for class '${type.name}'")
            }
        }
        index.compile(ctx)
        if (type !is Indexable) {
            throw IllegalArgumentException("Index on non-indexable type $type")
        }
        return type.compileIndex(ctx)
    }

    override fun compileAssignment(type: Type, ctx: Context) {
        val listType = list.compile(ctx)
        if (listType is ClassType) {
            val id = opTempCounter.getAndIncrement()
            val instanceTemp = ctx.def("@op_inst$id", listType)
            ctx.add(Store(instanceTemp.index))
            val valueTemp = ctx.def("@op_val$id", type)
            ctx.add(Store(valueTemp.index))
            ctx.add(Load(instanceTemp.index))
            ctx.add(Load(valueTemp.index))
            val indexType = index.compile(ctx)
            val prop = ClassElementProp("op@[]=")
            try {
                prop.compile(listType, args = listOf(type, indexType), ctx)
            } catch (e: IllegalArgumentException) {
                throw IllegalArgumentException("Operator '[]=' is not defined for class '${listType.name}'")
            }
            return
        }
        index.compile(ctx)
        if (listType !is IndexAssignable) {
            throw IllegalArgumentException("Assign on non-assignable index type $listType")
        }
        listType.compileAssignment(ctx)
    }
}
