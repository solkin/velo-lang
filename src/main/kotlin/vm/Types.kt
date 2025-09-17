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

class VmFunc : VmType {
    override fun toJvmType() = throw IllegalArgumentException("Inconvertible type VmFunc")
}
