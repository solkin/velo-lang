package vm

import java.lang.reflect.Array as JArray

interface VmType {
    fun toJvmType(): Class<*>
}

class VmVoid : VmType {
    override fun toJvmType() = Void::class.java
}

class VmAny : VmType {
    override fun toJvmType() = Any::class.java
}

class VmByte : VmType {
    override fun toJvmType() = Byte::class.java
}

class VmInt : VmType {
    override fun toJvmType() = Int::class.java
}

class VmFloat : VmType {
    override fun toJvmType() = Float::class.java
}

class VmStr : VmType {
    override fun toJvmType() = String::class.java
}

class VmBool : VmType {
    override fun toJvmType() = Boolean::class.java
}

class VmTuple(val elementTypes: List<VmType> = emptyList()) : VmType {
    override fun toJvmType() = Array::class.java
}

/**
 * Array type with element type information for proper JVM type mapping.
 */
class VmArray(val elementType: VmType = VmAny()) : VmType {
    override fun toJvmType(): Class<*> {
        // For arrays, we need boxed types to match Kotlin's Array<T> signatures
        val elementClass = elementType.toBoxedJvmType()
        return JArray.newInstance(elementClass, 0)::class.java
    }
}

/**
 * Get the boxed JVM class for a VmType (e.g., java.lang.Integer instead of int).
 * Used for array element types since Kotlin's Array<T> uses boxed types.
 */
fun VmType.toBoxedJvmType(): Class<*> {
    return when (this) {
        is VmByte -> java.lang.Byte::class.java
        is VmInt -> java.lang.Integer::class.java
        is VmFloat -> java.lang.Float::class.java
        is VmBool -> java.lang.Boolean::class.java
        else -> toJvmType()
    }
}

/**
 * Dictionary type with key and value type information.
 */
class VmDict(val keyType: VmType = VmAny(), val valueType: VmType = VmAny()) : VmType {
    override fun toJvmType() = Map::class.java
}

class VmClass(val name: String) : VmType {
    override fun toJvmType(): Class<*> = Class.forName(name)
}

class VmFunc : VmType {
    override fun toJvmType() = throw IllegalArgumentException("Inconvertible type VmFunc")
}

class VmPtr(val derived: VmType) : VmType {
    override fun toJvmType() = throw IllegalArgumentException("Inconvertible type VmPtr")
}
