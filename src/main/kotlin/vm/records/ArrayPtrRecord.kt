package vm.records

import vm.Record

/**
 * A pointer that references an element in an array.
 * Created by the address-of operator on array elements `&array[index]`.
 */
data class ArrayPtrRecord(
    private val array: Array<Record>,
    private val index: Int
) : PtrRecord {

    override fun deref(): Record {
        if (index < 0 || index >= array.size) {
            throw IndexOutOfBoundsException("Array pointer index $index out of bounds [0, ${array.size})")
        }
        return array[index]
    }

    override fun assign(value: Record) {
        if (index < 0 || index >= array.size) {
            throw IndexOutOfBoundsException("Array pointer index $index out of bounds [0, ${array.size})")
        }
        array[index] = value
    }

    override fun isNull(): Boolean = false

    override fun toString(): String = "ptr -> array[$index]"

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ArrayPtrRecord) return false
        return array === other.array && index == other.index
    }

    override fun hashCode(): Int {
        return System.identityHashCode(array) * 31 + index
    }
}

