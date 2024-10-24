package vm.records

import vm.Record

data class ValueRecord(
    private val value: Any
) : Record {

    override fun get(): Any = value

}
