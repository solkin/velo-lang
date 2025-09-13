package vm

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

class VmTuple : VmType {
    override fun toJvmType() = Array::class.java
}

class VmArray : VmType {
    override fun toJvmType() = Array::class.java
}

class VmDict : VmType {
    override fun toJvmType() = Map::class.java
}

class VmClass(val name: String) : VmType {
    override fun toJvmType(): Class<*> = Class.forName(name)
}

class VmFunc() : VmType {
    override fun toJvmType() = throw IllegalArgumentException("Inconvertible type VmFunc")
}

//const val VOID: Byte = 0x00
//const val ANY: Byte = 0x01
//const val BYTE: Byte = 0x02
//const val INT: Byte = 0x03
//const val FLOAT: Byte = 0x04
//const val STR: Byte = 0x05
//const val BOOL: Byte = 0x06
//const val TUPLE: Byte = 0x07
//const val ARRAY: Byte = 0x08
//const val DICT: Byte = 0x09
//const val CLASS: Byte = 0x0a
//const val FUNC: Byte = 0x0b
