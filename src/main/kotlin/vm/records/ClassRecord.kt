package vm.records

import vm.Heap
import vm.Record
import vm.VMContext

data class ClassRecord(
    val id: Int,
    val nativeIndex: Int?,
    @Transient private val heap: Heap? = null
) : Record {

    override fun <T> get(): T {
        val h = heap ?: throw IllegalStateException("ClassRecord requires heap context for get()")
        return h.get(id)
    }

    fun <T> get(ctx: VMContext): T = ctx.heapGet(id)

    companion object {
        fun create(value: Any, nativeIndex: Int?, ctx: VMContext): ClassRecord {
            return ClassRecord(id = ctx.heapPut(value), nativeIndex, ctx.heap)
        }
    }
}
