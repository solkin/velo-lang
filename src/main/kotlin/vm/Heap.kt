package vm

import vm.records.LinkRecord
import java.util.concurrent.atomic.AtomicInteger

interface Heap {
    fun put(value: Any): LinkRecord
    fun <T> get(link: LinkRecord): T
}

object GlobalHeap : Heap {

    private val enumerator = AtomicInteger()
    private val heap = HashMap<Int, Any>()

    override fun put(value: Any) : LinkRecord {
        val id = enumerator.getAndIncrement()
        heap[id] = value
        return LinkRecord(id)
    }

    @Suppress("UNCHECKED_CAST")
    override fun <T> get(link: LinkRecord): T {
        return heap[link.id] as T ?: throw Exception("Broken heap link: $link")
    }

}