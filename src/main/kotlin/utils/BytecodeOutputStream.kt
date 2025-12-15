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
import vm.VmPtr
import vm.VmStr
import vm.VmTuple
import vm.VmType
import vm.VmVoid
import vm.operations.Abs
import vm.operations.And
import vm.operations.ArrLen
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
import vm.operations.Div
import vm.operations.Pop
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
import vm.operations.ArrCopy
import vm.operations.ArrLoad
import vm.operations.ArrNew
import vm.operations.ArrStore
import vm.operations.Hash
import vm.operations.Push
import vm.operations.Rem
import vm.operations.Ret
import vm.operations.Rot
import vm.operations.Store
import vm.operations.PtrLoad
import vm.operations.PtrNew
import vm.operations.PtrRef
import vm.operations.PtrRefIndex
import vm.operations.PtrStore
import vm.operations.Shl
import vm.operations.Shr
import vm.operations.StrCon
import vm.operations.StrIndex
import vm.operations.StrInt
import vm.operations.StrLen
import vm.operations.StrSub
import vm.operations.Swap
import java.io.DataOutputStream
import java.io.OutputStream

class BytecodeOutputStream(
    private val out: DataOutputStream
) : OutputStream() {

    init {
        with(out) {
            writeShort(MAGIC)
            writeByte(VERSION_MAJOR)
            writeByte(VERSION_MINOR)
        }
    }

    fun write(frames: List<SerializedFrame>) {
        out.writeShort(frames.size)
        frames.forEach { frame ->
            write(frame)
        }
    }

    fun write(frame: SerializedFrame) {
        out.writeShort(frame.num)
        writeVars(frame.vars)
        writeOps(frame.ops)
    }

    fun writeVars(vars: List<Int>) {
        out.writeShort(vars.size)
        vars.forEach { v ->
            out.writeShort(v)
        }
    }

    fun writeOps(ops: List<Operation>) {
        out.writeShort(ops.size)
        ops.forEach { op ->
            write(op)
        }
    }

    private fun write(op: Operation) {
        when (op) {
            is Abs -> out.writeByte(0x01)
            is And -> out.writeByte(0x02)
            is ArrNew -> out.writeByte(0x03)
            is ArrLoad -> out.writeByte(0x04)
            is ArrLen -> out.writeByte(0x05)
            is ArrStore -> out.writeByte(0x08)
            is Call -> out.writeByte(0x09).also {
                out.writeInt(op.args)
                out.writeBoolean(op.classParent)
            }

            is Div -> out.writeByte(0x0b)
            is Pop -> out.writeByte(0x0c)
            is Dup -> out.writeByte(0x0d)
            is Equals -> out.writeByte(0x0e)
            is Load -> out.writeByte(0x0f).also { out.writeInt(op.index) }
            is Halt -> out.writeByte(0x11)
            is If -> out.writeByte(0x12).also { out.writeInt(op.elseSkip) }
            is IntChar -> out.writeByte(0x14)
            is IntStr -> out.writeByte(0x15)
            is Frame -> out.writeByte(0x18).also { out.writeInt(op.num) }
            is Sub -> out.writeByte(0x1a)
            is More -> out.writeByte(0x1b)
            is Move -> out.writeByte(0x1d).also { out.writeInt(op.count) }
            is Mul -> out.writeByte(0x1e)
            is Inv -> out.writeByte(0x20)
            is Or -> out.writeByte(0x21)
            is Add -> out.writeByte(0x26)
            is Push -> out.writeByte(0x29).also { out.write(op.value) }
            is Rem -> out.writeByte(0x2a)
            is Ret -> out.writeByte(0x2b)
            is Rot -> out.writeByte(0x2c)
            is Store -> out.writeByte(0x2d).also { out.writeInt(op.index) }
            is StrCon -> out.writeByte(0x2e)
            is StrIndex -> out.writeByte(0x2f)
            is StrLen -> out.writeByte(0x30)
            is ArrCopy -> out.writeByte(0x32)
            is StrSub -> out.writeByte(0x33)
            is Swap -> out.writeByte(0x34)
            is DictArr -> out.writeByte(0x36)
            is DictDel -> out.writeByte(0x37)
            is DictIndex -> out.writeByte(0x38)
            is DictKey -> out.writeByte(0x39)
            is DictKeys -> out.writeByte(0x3a)
            is DictLen -> out.writeByte(0x3b)
            is DictOf -> out.writeByte(0x3c)
            is DictSet -> out.writeByte(0x3d)
            is DictVal -> out.writeByte(0x3e)
            is DictVals -> out.writeByte(0x3f)
            is StrInt -> out.writeByte(0x40)

            is Instance -> out.writeByte(0x42).also {
                out.writeNullableInt(op.nativeIndex)
            }
            is NativeConstructor -> out.writeByte(0x43).also {
                out.writeUTF(op.name)
                out.writeByte(op.args.size)
                op.args.forEach { arg ->
                    out.writeInt(arg.first)
                    out.writeType(arg.second)
                }
            }

            is NativeFunction -> out.writeByte(0x44).also {
                out.writeUTF(op.name)
                out.writeByte(op.argTypes.size)
                op.argTypes.forEach { arg ->
                    out.writeType(arg)
                }
            }

            is NativeInvoke -> out.writeByte(0x45).also {
                out.writeByte(op.args.size)
                op.args.forEach { arg ->
                    out.writeInt(arg.first)
                    out.writeType(arg.second)
                }
            }

            is Shl -> out.writeByte(0x46)
            is Shr -> out.writeByte(0x47)
            is Hash -> out.writeByte(0x48)

            // Pointer operations
            is PtrNew -> out.writeByte(0x50)
            is PtrLoad -> out.writeByte(0x51)
            is PtrStore -> out.writeByte(0x52)
            is PtrRef -> out.writeByte(0x53).also { out.writeInt(op.varIndex) }
            is PtrRefIndex -> out.writeByte(0x54)

            else -> throw IllegalArgumentException("Operation $op is not supported")
        }
    }

    override fun write(b: Int) {
        out.write(b)
    }

}

fun DataOutputStream.writeNullableInt(v: Int?) {
    writeBoolean(v != null)
    if (v != null) {
        writeInt(v)
    }
}

private fun DataOutputStream.write(value: Any) {
    when (value) {
        is Byte -> {
            writeByte(TYPE_BYTE)
            writeByte(value.toInt())
        }

        is Int -> {
            writeByte(TYPE_INT)
            writeInt(value)
        }

        is Float -> {
            writeByte(TYPE_FLOAT)
            writeFloat(value)
        }

        is String -> {
            writeByte(TYPE_STR)
            writeUTF(value)
        }

        is Boolean -> {
            writeByte(TYPE_BOOL)
            writeBoolean(value)
        }

        is vm.records.NullPtrRecord -> {
            writeByte(TYPE_NULL_PTR)
        }
    }
}

private fun DataOutputStream.writeType(t: VmType) {
    when(t) {
        is VmVoid -> writeByte(TYPE_VOID)
        is VmAny -> writeByte(TYPE_ANY)
        is VmByte -> writeByte(TYPE_BYTE)
        is VmInt -> writeByte(TYPE_INT)
        is VmFloat -> writeByte(TYPE_FLOAT)
        is VmStr -> writeByte(TYPE_STR)
        is VmBool -> writeByte(TYPE_BOOL)
        is VmTuple -> writeByte(TYPE_TUPLE)
        is VmArray -> writeByte(TYPE_ARRAY)
        is VmDict -> writeByte(TYPE_DICT)
        is VmClass -> writeByte(TYPE_CLASS).also { writeUTF(t.name) }
        is VmFunc -> writeByte(TYPE_FUNC)
        is VmPtr -> writeByte(TYPE_PTR).also { writeType(t.derived) }
    }
}

const val MAGIC = 0x5e10
const val VERSION_MAJOR = 0x07
const val VERSION_MINOR = 0x0d

const val TYPE_VOID = 0x00
const val TYPE_ANY = 0x01
const val TYPE_BYTE = 0x02
const val TYPE_INT = 0x03
const val TYPE_FLOAT = 0x04
const val TYPE_STR = 0x05
const val TYPE_BOOL = 0x06
const val TYPE_TUPLE = 0x07
const val TYPE_ARRAY = 0x08
const val TYPE_DICT = 0x09
const val TYPE_CLASS = 0x0a
const val TYPE_FUNC = 0x0b
const val TYPE_PTR = 0x0c
const val TYPE_NULL_PTR = 0x0d
