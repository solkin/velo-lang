package vm2

interface Record {

    fun get(): Any

    fun getBool(): Boolean = get() as Boolean

    fun getByte(): Byte = get() as Byte

    fun getInt(): Int = get() as Int

    fun getLong(): Long  = get() as Long

    fun getFloat(): Float = get() as Float

    fun getDouble(): Double = get() as Double

    fun getChar(): Char = get() as Char

    fun getString(): String = get() as String

}
