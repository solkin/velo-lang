package utils

import vm.Operation
import vm.VmAny
import vm.VmArray
import vm.VmBool
import vm.VmByte
import vm.VmClass
import vm.VmDict
import vm.VmFloat
import vm.VmFunc
import vm.VmInt
import vm.VmStr
import vm.VmTuple
import vm.VmType
import vm.VmVoid
import vm.operations.Abs
import vm.operations.And
import vm.operations.ArrCon
import vm.operations.ArrIndex
import vm.operations.ArrLen
import vm.operations.ArrOf
import vm.operations.ArrSet
import vm.operations.ArrSub
import vm.operations.Call
import vm.operations.DictArr
import vm.operations.DictDel
import vm.operations.DictIndex
import vm.operations.DictKey
import vm.operations.DictKeys
import vm.operations.DictLen
import vm.operations.DictOf
import vm.operations.DictSet
import vm.operations.DictVal
import vm.operations.DictVals
import vm.operations.Divide
import vm.operations.Drop
import vm.operations.Dup
import vm.operations.Equals
import vm.operations.Frame
import vm.operations.Load
import vm.operations.Halt
import vm.operations.If
import vm.operations.Instance
import vm.operations.IntChar
import vm.operations.IntStr
import vm.operations.Inv
import vm.operations.Sub
import vm.operations.More
import vm.operations.Move
import vm.operations.Mul
import vm.operations.NativeConstructor
import vm.operations.NativeFunction
import vm.operations.NativeInvoke
import vm.operations.Or
import vm.operations.Add
import vm.operations.Push
import vm.operations.Rem
import vm.operations.Ret
import vm.operations.Rot
import vm.operations.Store
import vm.operations.Shl
import vm.operations.Shr
import vm.operations.StrCon
import vm.operations.StrIndex
import vm.operations.StrInt
import vm.operations.StrLen
import vm.operations.SubStr
import vm.operations.Swap
import vm.operations.Xor
import java.io.DataInputStream
import java.io.InputStream

class BytecodeInputStream(
    private val inp: DataInputStream
) : InputStream() {

    init {
        val magic = inp.readShort().toInt()
        val versionMajor = inp.readByte().toInt()
        val versionMinor = inp.readByte().toInt()

        if (magic != MAGIC) {
            throw IllegalArgumentException("Invalid file, magic code wrong: $magic")
        }
        if (versionMajor != VERSION_MAJOR) {
            throw IllegalArgumentException("Unsupported file version: $versionMajor.$versionMinor")
        }
    }

    fun readFrames(): List<SerializedFrame> {
        val count = inp.readShort().toInt()
        return ArrayList<SerializedFrame>(count).apply {
            repeat(count) {
                val frame = readFrame()
                add(frame)
            }
        }
    }

    fun readFrame(): SerializedFrame {
        val num = inp.readShort().toInt()
        val vars = readVars()
        val ops = readOperations()
        return SerializedFrame(num, ops.toMutableList(), vars.toMutableList())
    }

    fun readVars(): List<Int> {
        val count = inp.readShort().toInt()
        return ArrayList<Int>(count).apply {
            repeat(count) {
                val v = inp.readShort()
                add(v.toInt())
            }
        }
    }

    fun readOperations(): List<Operation> {
        val count = inp.readShort().toInt()
        return ArrayList<Operation>(count).apply {
            repeat(count) {
                val op = readOperation()
                add(op)
            }
        }
    }

    private fun readOperation(): Operation {
        return when (val opcode = inp.readByte().toInt()) {
            0x01 -> Abs()
            0x02 -> And()
            0x03 -> ArrCon()
            0x04 -> ArrIndex()
            0x05 -> ArrLen()
            0x06 -> ArrOf()
            0x08 -> ArrSet()
            0x09 -> Call(args = inp.readInt(), classParent = inp.readBoolean())
            0x0b -> Divide()
            0x0c -> Drop()
            0x0d -> Dup()
            0x0e -> Equals()
            0x0f -> Load(index = inp.readInt())
            0x11 -> Halt()
            0x12 -> If(elseSkip = inp.readInt())
            0x14 -> IntChar()
            0x15 -> IntStr()
            0x18 -> Frame(num = inp.readInt())
            0x1a -> Sub()
            0x1b -> More()
            0x1d -> Move(count = inp.readInt())
            0x1e -> Mul()
            0x20 -> Inv()
            0x21 -> Or()
            0x26 -> Add()
            0x29 -> Push(value = inp.readAny())
            0x2a -> Rem()
            0x2b -> Ret()
            0x2c -> Rot()
            0x2d -> Store(index = inp.readInt())
            0x2e -> StrCon()
            0x2f -> StrIndex()
            0x30 -> StrLen()
            0x32 -> ArrSub()
            0x33 -> SubStr()
            0x34 -> Swap()
            0x35 -> Xor()
            0x36 -> DictArr()
            0x37 -> DictDel()
            0x38 -> DictIndex()
            0x39 -> DictKey()
            0x3a -> DictKeys()
            0x3b -> DictLen()
            0x3c -> DictOf()
            0x3d -> DictSet()
            0x3e -> DictVal()
            0x3f -> DictVals()
            0x40 -> StrInt()
            0x42 -> Instance(nativeIndex = inp.readNullableInt())
            0x43 -> NativeConstructor(
                name = inp.readUTF(),
                args = inp.readArray { Pair(inp.readInt(), inp.readType()) }
            )

            0x44 -> NativeFunction(
                name = inp.readUTF(),
                argTypes = inp.readArray { inp.readType() }
            )

            0x45 -> NativeInvoke(
                args = inp.readArray { Pair(inp.readInt(), inp.readType()) }
            )

            0x46 -> Shl()
            0x47 -> Shr()

            else -> throw IllegalStateException("Unsupported opcode: $opcode")
        }
    }

    override fun read(): Int {
        return inp.read()
    }
}

private fun DataInputStream.readAny(): Any {
    return when (val type = readByte().toInt()) {
        TYPE_BYTE -> readByte()
        TYPE_INT -> readInt()
        TYPE_FLOAT -> readFloat()
        TYPE_STR -> readUTF()
        TYPE_BOOL -> readBoolean()
        else -> throw IllegalStateException("Unsupported data type: $type")
    }
}

private fun <T> DataInputStream.readArray(action: () -> T): List<T> {
    val size = readByte().toInt()
    val result = ArrayList<T>()
    repeat(size) {
        result.add(action())
    }
    return result
}

private fun DataInputStream.readType(): VmType {
    return when (val t = readByte().toInt()) {
        TYPE_VOID -> VmVoid()
        TYPE_ANY -> VmAny()
        TYPE_BYTE -> VmByte()
        TYPE_INT -> VmInt()
        TYPE_FLOAT -> VmFloat()
        TYPE_STR -> VmStr()
        TYPE_BOOL -> VmBool()
        TYPE_TUPLE -> VmTuple()
        TYPE_ARRAY -> VmArray()
        TYPE_DICT -> VmDict()
        TYPE_CLASS -> VmClass(name = readUTF())
        TYPE_FUNC -> VmFunc()
        else -> throw Exception("Unsupported bytecode data type $t")
    }
}

private fun DataInputStream.readNullableInt(): Int? {
    if (readBoolean()) {
        return readInt()
    }
    return null
}
