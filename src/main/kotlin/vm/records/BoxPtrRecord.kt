package vm.records

import vm.Record

/**
 * A pointer that acts as a "box" - a mutable container for a value.
 * This is the primary way to create pointers with `new ptr[T](value)`.
 */
data class BoxPtrRecord(
    private var value: Record?
) : PtrRecord {

    override fun deref(): Record {
        return value ?: throw NullPointerException("Dereferencing null pointer")
    }

    override fun assign(newValue: Record) {
        value = newValue
    }

    override fun isNull(): Boolean = value == null

    override fun toString(): String = "ptr -> ${value ?: "null"}"
}

