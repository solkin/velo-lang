package vm.records

import vm.Record
import vm.Vars

/**
 * A pointer that references a variable in the frame's variable storage.
 * Created by the address-of operator `&variable`.
 */
data class VarPtrRecord(
    private val vars: Vars,
    private val index: Int
) : PtrRecord {

    override fun deref(): Record {
        return vars.get(index)
    }

    override fun assign(value: Record) {
        vars.set(index, value)
    }

    override fun isNull(): Boolean = false

    override fun toString(): String = "ptr -> var[$index]"
}

