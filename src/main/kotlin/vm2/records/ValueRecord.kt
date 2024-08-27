package vm2.records

import vm2.Record

class ValueRecord(
    private val value: Any
) : Record {

    override fun get(): Any = value

}