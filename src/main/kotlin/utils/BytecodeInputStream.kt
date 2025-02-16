package utils

import vm.Operation
import vm.operations.Abs
import vm.operations.And
import vm.operations.ArrCon
import vm.operations.ArrIndex
import vm.operations.ArrLen
import vm.operations.ArrOf
import vm.operations.ArrPlus
import vm.operations.ArrSet
import vm.operations.Call
import vm.operations.Def
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
import vm.operations.Get
import vm.operations.Goto
import vm.operations.Halt
import vm.operations.If
import vm.operations.Input
import vm.operations.IntChar
import vm.operations.IntStr
import vm.operations.Less
import vm.operations.LessEquals
import vm.operations.MakePtr
import vm.operations.MakeStruct
import vm.operations.Minus
import vm.operations.More
import vm.operations.MoreEquals
import vm.operations.Move
import vm.operations.Multiply
import vm.operations.Negative
import vm.operations.Not
import vm.operations.Or
import vm.operations.Pair
import vm.operations.PairFirst
import vm.operations.PairSecond
import vm.operations.Pick
import vm.operations.Plus
import vm.operations.Print
import vm.operations.Println
import vm.operations.Push
import vm.operations.Rem
import vm.operations.Ret
import vm.operations.Rot
import vm.operations.Set
import vm.operations.StrCon
import vm.operations.StrIndex
import vm.operations.StrInt
import vm.operations.StrLen
import vm.operations.StructElement
import vm.operations.SubArr
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
            0x07 -> ArrPlus()
            0x08 -> ArrSet()
            0x09 -> Call(args = inp.readInt())
            0x0a -> Def(index = inp.readInt())
            0x0b -> Divide()
            0x0c -> Drop()
            0x0d -> Dup()
            0x0e -> Equals()
            0x0f -> Get(index = inp.readInt())
            0x10 -> Goto(addr = inp.readInt())
            0x11 -> Halt()
            0x12 -> If(elseSkip = inp.readInt())
            0x13 -> Input()
            0x14 -> IntChar()
            0x15 -> IntStr()
            0x16 -> Less()
            0x17 -> LessEquals()
            0x18 -> MakePtr(diff = inp.readInt())
            0x19 -> MakeStruct()
            0x1a -> Minus()
            0x1b -> More()
            0x1c -> MoreEquals()
            0x1d -> Move(count = inp.readInt())
            0x1e -> Multiply()
            0x1f -> Negative()
            0x20 -> Not()
            0x21 -> Or()
            0x22 -> Pair()
            0x23 -> PairFirst()
            0x24 -> PairSecond()
            0x25 -> Pick()
            0x26 -> Plus()
            0x27 -> Print()
            0x28 -> Println()
            0x29 -> Push(value = readAny())
            0x2a -> Rem()
            0x2b -> Ret()
            0x2c -> Rot()
            0x2d -> Set(index = inp.readInt())
            0x2e -> StrCon()
            0x2f -> StrIndex()
            0x30 -> StrLen()
            0x31 -> StructElement()
            0x32 -> SubArr()
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
            else -> throw IllegalStateException("Unsupported opcode: $opcode")
        }
    }

    override fun read(): Int {
        return inp.read()
    }

    private fun readAny(): Any {
        return when (val type = inp.readByte().toInt()) {
            BC_TYPE_BYTE -> inp.readByte()
            BC_TYPE_INT -> inp.readInt()
            BC_TYPE_FLOAT -> inp.readFloat()
            BC_TYPE_STRING -> inp.readUTF()
            BC_TYPE_BOOLEAN -> inp.readBoolean()
            else -> throw IllegalStateException("Unsupported data type: $type")
        }
    }
}