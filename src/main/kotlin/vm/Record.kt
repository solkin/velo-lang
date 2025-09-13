package vm

import vm.records.ClassRecord

interface Record {

    fun <T> get(): T

    fun <T> cast(targetClass: Class<T>): T {
        return targetClass.cast(get())
    }

    fun getBool(): Boolean = get()

    fun getNumber(): Number = get()

    fun getByte(): Byte = get()

    fun getInt(): Int = get()

    fun getLong(): Long = get()

    fun getFloat(): Float = get()

    fun getDouble(): Double = get()

    fun getChar(): Char = get()

    fun getString(): String = get()

    fun getArray(): Array<Record> = get()

    fun getDict(): MutableMap<Record, Record> = get()

    fun getFrame(): Frame = get()

    fun getAs(vmType: VmType): Any {
        return when (vmType) {
            is VmByte -> getByte()
            is VmAny -> get()
            is VmInt -> getInt()
            is VmFloat -> getFloat()
            is VmStr -> getString()
            is VmBool -> getBool()
            is VmTuple -> getArray()
            is VmArray -> getArray()
            is VmDict -> getDict()
            is VmClass -> {
                if (this !is ClassRecord) throw IllegalStateException("Non-native class record")
                val index = nativeIndex ?: throw IllegalStateException("Native index is not defined")
                val frame = getFrame()
                frame.vars.get(index).cast(vmType.toJvmType())
            }
            else -> throw IllegalArgumentException("Inconvertible type $vmType")
        }
    }

}
