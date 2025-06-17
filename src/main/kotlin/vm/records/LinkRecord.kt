package vm.records

import vm.HeapArea
import vm.Record

data class LinkRecord(
    val id: Int
) : Record {

    override fun <T> get(): T = HeapArea.get(this)

    companion object {
        fun create(value: Any): LinkRecord {
            return HeapArea.put(value)
        }
    }
}
