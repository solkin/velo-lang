package vm.records

import vm.Heap
import vm.Record
import vm.VMContext

data class LinkRecord(
    val id: Int,
    @Transient private val heap: Heap? = null
) : Record {

    override fun <T> get(): T {
        val h = heap ?: throw IllegalStateException("LinkRecord requires heap context for get()")
        return h.get(id)
    }

    fun <T> get(ctx: VMContext): T = ctx.heapGet(id)

    companion object {
        fun create(value: Any, ctx: VMContext): LinkRecord {
            return LinkRecord(id = ctx.heapPut(value), heap = ctx.heap)
        }
    }
}
