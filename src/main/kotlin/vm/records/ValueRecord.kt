package vm.records

import vm.Record

data class ValueRecord(
    private val value: Any
) : Record {

    @Suppress("UNCHECKED_CAST")
    override fun <T> get(): T = value as T

}
