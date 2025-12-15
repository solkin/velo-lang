package vm.records

import vm.Record

/**
 * Interface for pointer records that can reference and modify values.
 */
interface PtrRecord : Record {
    /**
     * Dereferences the pointer to get the underlying value.
     */
    fun deref(): Record

    /**
     * Assigns a new value through this pointer.
     */
    fun assign(value: Record)

    /**
     * Checks if this pointer is null (doesn't point to anything).
     */
    fun isNull(): Boolean

    @Suppress("UNCHECKED_CAST")
    override fun <T> get(): T = deref() as T
}

