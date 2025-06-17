package vm.records

import vm.NativeArea
import vm.Record

data class NativeRecord(
    val id: Int
) : Record {

    override fun <T> get(): T = NativeArea.get(this)

    companion object {
        fun create(value: Any): NativeRecord {
            return NativeArea.put(value)
        }
    }
}
