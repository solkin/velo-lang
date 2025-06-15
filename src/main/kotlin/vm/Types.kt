package vm

const val VOID: Byte = 0x00
const val AUTO: Byte = 0x01
const val BYTE: Byte = 0x02
const val INT: Byte = 0x03
const val FLOAT: Byte = 0x04
const val STR: Byte = 0x05
const val BOOL: Byte = 0x06
const val PAIR: Byte = 0x07
const val ARRAY: Byte = 0x08
const val DICT: Byte = 0x09
const val CLASS: Byte = 0x0a
const val FUNC: Byte = 0x0b

fun Byte.toJvmType(): Class<*> {
    val vmType = this
    return when(vmType) {
        VOID -> Void::class.java
        BYTE -> Byte::class.java
        INT -> Int::class.java
        FLOAT -> Float::class.java
        STR -> String::class.java
        BOOL -> Boolean::class.java
        PAIR -> Pair::class.java
        ARRAY -> Array::class.java
        DICT -> Map::class.java
        else -> throw IllegalArgumentException("Inconvertible type $vmType")
    }
}

fun Record.toType(vmType: Byte): Any {
    return when(vmType) {
        BYTE -> getByte()
        INT -> getInt()
        FLOAT -> getFloat()
        STR -> getString()
        BOOL -> getBool()
        PAIR -> getPair()
        ARRAY -> getArray()
        DICT -> getDict()
        else -> throw IllegalArgumentException("Inconvertible type $vmType")
    }
}