package vm.records

import vm.Record

data class PointerRecord(
    private val pointer: Int
) : Record {

    @Suppress("UNCHECKED_CAST")
    override fun <T> get(): T = pointer as T

}
