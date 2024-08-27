package vm2.records

import vm2.Record
import vm2.Scope

class PointerRecord(
    private val scope: Scope<Record>,
    private val index: Int,
) : Record {

    override fun get(): Any = scope.get(index).get()

    fun set(value: Any) {
        scope.set(index, ValueRecord(value))
    }

}