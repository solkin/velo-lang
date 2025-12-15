package vm.records

import vm.Record

/**
 * Represents a null pointer. Used as the default value for pointer types.
 */
object NullPtrRecord : PtrRecord {

    override fun deref(): Record {
        throw NullPointerException("Dereferencing null pointer")
    }

    override fun assign(value: Record) {
        throw NullPointerException("Cannot assign through null pointer")
    }

    override fun isNull(): Boolean = true

    override fun toString(): String = "ptr -> null"
}

