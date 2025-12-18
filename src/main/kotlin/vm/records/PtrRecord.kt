package vm.records

import vm.Record
import vm.Vars

/**
 * Sealed class for all pointer record types.
 * Pointers can reference and modify values in different storage locations.
 */
sealed class PtrRecord : Record {
    /**
     * Dereferences the pointer to get the underlying value.
     */
    abstract fun deref(): Record

    /**
     * Assigns a new value through this pointer.
     */
    abstract fun assign(value: Record)

    /**
     * Checks if this pointer is null (doesn't point to anything).
     */
    abstract fun isNull(): Boolean

    @Suppress("UNCHECKED_CAST")
    override fun <T> get(): T = deref() as T

    /**
     * A pointer that references a variable in the frame's variable storage.
     * Created by the address-of operator `&variable`.
     */
    data class Var(
        private val vars: Vars,
        private val index: Int
    ) : PtrRecord() {
        override fun deref(): Record = vars.get(index)
        override fun assign(value: Record) { vars.set(index, value) }
        override fun isNull(): Boolean = false
        override fun toString(): String = "ptr -> var[$index]"
    }

    /**
     * A pointer that references an element in an array.
     * Created by the address-of operator on array elements `&array[index]`.
     */
    data class Array(
        private val array: kotlin.Array<Record>,
        private val index: Int
    ) : PtrRecord() {
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
            if (other !is Array) return false
            return array === other.array && index == other.index
        }

        override fun hashCode(): Int {
            return System.identityHashCode(array) * 31 + index
        }
    }

    /**
     * A pointer that acts as a "box" - a mutable container for a value.
     * This is the primary way to create pointers with `new ptr[T](value)`.
     */
    data class Box(
        private var value: Record?
    ) : PtrRecord() {
        override fun deref(): Record {
            return value ?: throw NullPointerException("Dereferencing null pointer")
        }

        override fun assign(newValue: Record) {
            value = newValue
        }

        override fun isNull(): Boolean = value == null
        override fun toString(): String = "ptr -> ${value ?: "null"}"
    }

    /**
     * Represents a null pointer. Used as the default value for pointer types.
     */
    object Null : PtrRecord() {
        override fun deref(): Record {
            throw NullPointerException("Dereferencing null pointer")
        }

        override fun assign(value: Record) {
            throw NullPointerException("Cannot assign through null pointer")
        }

        override fun isNull(): Boolean = true
        override fun toString(): String = "ptr -> null"
    }
}
