package vm

interface Record {

    fun <T> get(): T

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

    fun getPair(): Pair<Record, Record> = get()

    fun getStruct(): ArrayList<Record> = get()

    fun getFrame(): Frame = get()

}
