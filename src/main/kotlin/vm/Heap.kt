package vm

import vm.records.LinkRecord
import java.util.concurrent.atomic.AtomicInteger

interface Heap {
    fun put(value: Any): Int
    fun <T> get(id: Int): T
}

object HeapArea : Heap {

    private val enumerator = AtomicInteger()
    private val area = HashMap<Int, Any>()

    override fun put(value: Any) : Int {
        val id = enumerator.getAndIncrement()
        area[id] = value
        return id
    }

    @Suppress("UNCHECKED_CAST")
    override fun <T> get(id: Int): T {
        return area[id] as T ?: throw Exception("Broken heap area link: $id")
    }

}
