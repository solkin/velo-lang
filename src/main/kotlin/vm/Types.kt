package vm

import java.lang.reflect.Array as JArray

/**
 * Sealed class representing all VM types.
 * Used for native interop type mapping.
 */
sealed class VmType {
    abstract fun toJvmType(): java.lang.Class<*>
    
    /**
     * Get the boxed JVM class (e.g., java.lang.Integer instead of int).
     * Used for array element types since Kotlin's Array<T> uses boxed types.
     */
    open fun toBoxedJvmType(): java.lang.Class<*> = toJvmType()

    object Void : VmType() {
        override fun toJvmType(): java.lang.Class<*> = java.lang.Void::class.java
    }

    object Any : VmType() {
        override fun toJvmType(): java.lang.Class<*> = kotlin.Any::class.java
    }

    object Byte : VmType() {
        override fun toJvmType(): java.lang.Class<*> = kotlin.Byte::class.java
        override fun toBoxedJvmType(): java.lang.Class<*> = java.lang.Byte::class.java
    }

    object Int : VmType() {
        override fun toJvmType(): java.lang.Class<*> = kotlin.Int::class.java
        override fun toBoxedJvmType(): java.lang.Class<*> = java.lang.Integer::class.java
    }

    object Float : VmType() {
        override fun toJvmType(): java.lang.Class<*> = kotlin.Float::class.java
        override fun toBoxedJvmType(): java.lang.Class<*> = java.lang.Float::class.java
    }

    object Str : VmType() {
        override fun toJvmType(): java.lang.Class<*> = String::class.java
    }

    object Bool : VmType() {
        override fun toJvmType(): java.lang.Class<*> = Boolean::class.java
        override fun toBoxedJvmType(): java.lang.Class<*> = java.lang.Boolean::class.java
    }

    data class Tuple(val elementTypes: List<VmType> = emptyList()) : VmType() {
        override fun toJvmType(): java.lang.Class<*> = kotlin.Array::class.java
    }

    data class Array(val elementType: VmType = Any) : VmType() {
        override fun toJvmType(): java.lang.Class<*> {
            val elementClass = elementType.toBoxedJvmType()
            return JArray.newInstance(elementClass, 0)::class.java
        }
    }

    data class Dict(val keyType: VmType = Any, val valueType: VmType = Any) : VmType() {
        override fun toJvmType(): java.lang.Class<*> = Map::class.java
    }

    data class Class(val name: String) : VmType() {
        override fun toJvmType(): java.lang.Class<*> = java.lang.Class.forName(name)
    }

    object Func : VmType() {
        override fun toJvmType(): java.lang.Class<*> = throw IllegalArgumentException("Inconvertible type VmType.Func")
    }

    data class Ptr(val derived: VmType) : VmType() {
        override fun toJvmType(): java.lang.Class<*> = throw IllegalArgumentException("Inconvertible type VmType.Ptr")
    }
}
