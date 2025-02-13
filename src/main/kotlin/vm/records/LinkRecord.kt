package vm.records

import vm.GlobalHeap
import vm.Record

data class LinkRecord(
    val id: Int
) : Record {

    override fun <T> get(): T = GlobalHeap.get(this)

    companion object {
        fun create(value: Any): LinkRecord {
            return GlobalHeap.put(value)
        }
    }
}
