package vm

interface Record {

    fun get(): Any

    fun getBool(): Boolean = get() as Boolean

    fun getByte(): Byte = get() as Byte

    fun getInt(): Int = get() as Int

    fun getLong(): Long = get() as Long

    fun getFloat(): Float = get() as Float

    fun getDouble(): Double = get() as Double

    fun getChar(): Char = get() as Char

    fun getString(): String = get() as String

    @Suppress("UNCHECKED_CAST")
    fun getArray(): Array<Record> = get() as Array<Record>

    @Suppress("UNCHECKED_CAST")
    fun getPair(): Pair<Record, Record> = get() as Pair<Record, Record>

    @Suppress("UNCHECKED_CAST")
    fun getStruct(): ArrayList<Record> = get() as ArrayList<Record>

}
