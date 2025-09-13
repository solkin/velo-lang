package vm.records

import vm.HeapArea
import vm.Record

data class ClassRecord(
    val id: Int,
    val nativeIndex: Int?,
) : Record {

    override fun <T> get(): T = HeapArea.get(id)

    companion object {
        fun create(value: Any, nativeIndex: Int?): ClassRecord {
            return ClassRecord(id = HeapArea.put(value), nativeIndex)
        }
    }
}
