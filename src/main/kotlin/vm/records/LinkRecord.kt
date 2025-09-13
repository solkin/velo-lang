package vm.records

import vm.HeapArea
import vm.Record

data class LinkRecord(
    val id: Int
) : Record {

    override fun <T> get(): T = HeapArea.get(id)

    companion object {
        fun create(value: Any): LinkRecord {
            return LinkRecord(id = HeapArea.put(value))
        }
    }
}
