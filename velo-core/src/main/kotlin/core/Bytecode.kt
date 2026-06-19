package core

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File

/**
 * The `.vbc` bytecode format — writer and reader side by side.
 *
 * Layout: magic, version, the native pool (every native entry point the
 * program references, resolved against the host registry at load time),
 * then the frames. Each instruction is encoded as its [Op.opcode] byte
 * followed by its operands; the operand layout of every op is defined by
 * the matching `writeOp`/`readOp` branches below — keep them in sync (the
 * round-trip test enforces this for the whole instruction set).
 */
object Bytecode {

    const val MAGIC = 0x5e10
    const val VERSION_MAJOR = 0x0b
    const val VERSION_MINOR = 0x00

    // Tags for inline values and serialized VmTypes.
    // 0x09 was TYPE_DICT — retired in v10 along with the dict opcodes.
    private const val TYPE_VOID = 0x00
    private const val TYPE_ANY = 0x01
    private const val TYPE_BYTE = 0x02
    private const val TYPE_INT = 0x03
    private const val TYPE_FLOAT = 0x04
    private const val TYPE_STR = 0x05
    private const val TYPE_BOOL = 0x06
    private const val TYPE_TUPLE = 0x07
    private const val TYPE_ARRAY = 0x08
    private const val TYPE_CLASS = 0x0a
    private const val TYPE_FUNC = 0x0b
    private const val TYPE_PTR = 0x0c
    private const val TYPE_NULL_PTR = 0x0d

    // ---- Writing ----

    fun write(program: SerializedProgram, file: File) {
        file.outputStream().use { fos ->
            DataOutputStream(fos.buffered()).use { out -> write(program, out) }
        }
    }

    fun write(program: SerializedProgram, out: DataOutputStream) {
        out.writeShort(MAGIC)
        out.writeByte(VERSION_MAJOR)
        out.writeByte(VERSION_MINOR)
        writeNatives(program.natives, out)
        writeDataClasses(program.dataClasses, out)
        out.writeShort(program.frames.size)
        program.frames.forEach { writeFrame(it, out) }
    }

    private fun writeDataClasses(dataClasses: List<DataClassInfo>, out: DataOutputStream) {
        out.writeShort(dataClasses.size)
        dataClasses.forEach { info ->
            out.writeInt(info.frameNum)
            out.writeUTF(info.name)
            out.writeByte(info.fields.size)
            info.fields.forEach { field ->
                out.writeUTF(field.name)
                out.writeInt(field.index)
                writeType(field.type, out)
            }
        }
    }

    private fun writeNatives(natives: List<NativeRef>, out: DataOutputStream) {
        out.writeShort(natives.size)
        natives.forEach { ref ->
            out.writeByte(if (ref.kind == NativeRef.Kind.CONSTRUCTOR) 0 else 1)
            out.writeUTF(ref.className)
            out.writeUTF(ref.methodName)
            out.writeByte(ref.params.size)
            ref.params.forEach { writeType(it, out) }
            writeType(ref.returns, out)
        }
    }

    private fun writeFrame(frame: SerializedFrame, out: DataOutputStream) {
        out.writeShort(frame.num)
        out.writeShort(frame.vars.size)
        frame.vars.forEach { out.writeShort(it) }
        out.writeShort(frame.ops.size)
        frame.ops.forEach { writeOp(it, out) }
    }

    private fun writeOp(op: Op, out: DataOutputStream) {
        out.writeByte(op.opcode)
        when (op) {
            is Op.Push -> writeValue(op.value, out)
            is Op.Load -> out.writeInt(op.index)
            is Op.Store -> out.writeInt(op.index)
            is Op.If -> out.writeInt(op.elseSkip)
            is Op.Move -> out.writeInt(op.count)
            is Op.Frame -> out.writeInt(op.num)
            is Op.PtrRef -> out.writeInt(op.varIndex)
            is Op.Call -> {
                out.writeInt(op.args)
                out.writeBoolean(op.classParent)
                out.writeBoolean(op.callbackResult)
            }
            is Op.NativeCall -> {
                out.writeShort(op.poolIndex)
                out.writeByte(op.args.size)
                op.args.forEach { writeType(it, out) }
            }
            is Op.ActorSpawn -> {
                out.writeInt(op.classFrameNum)
                out.writeUTF(op.className)
                out.writeInt(op.args)
            }
            is Op.ActorCall -> {
                out.writeInt(op.methodVarIndex)
                out.writeInt(op.args)
            }
            else -> Unit // the remaining ops carry no operands
        }
    }

    private fun writeValue(value: Any, out: DataOutputStream) {
        when (value) {
            is Byte -> {
                out.writeByte(TYPE_BYTE)
                out.writeByte(value.toInt())
            }
            is Int -> {
                out.writeByte(TYPE_INT)
                out.writeInt(value)
            }
            is Float -> {
                out.writeByte(TYPE_FLOAT)
                out.writeFloat(value)
            }
            is String -> {
                out.writeByte(TYPE_STR)
                out.writeUTF(value)
            }
            is Boolean -> {
                out.writeByte(TYPE_BOOL)
                out.writeBoolean(value)
            }
            NullPtr -> out.writeByte(TYPE_NULL_PTR)
            else -> throw IllegalArgumentException(
                "Push literal of type ${value::class.qualifiedName} is not serializable"
            )
        }
    }

    private fun writeType(t: VmType, out: DataOutputStream) {
        when (t) {
            is VmType.Void -> out.writeByte(TYPE_VOID)
            is VmType.Any -> out.writeByte(TYPE_ANY)
            is VmType.Byte -> out.writeByte(TYPE_BYTE)
            is VmType.Int -> out.writeByte(TYPE_INT)
            is VmType.Float -> out.writeByte(TYPE_FLOAT)
            is VmType.Str -> out.writeByte(TYPE_STR)
            is VmType.Bool -> out.writeByte(TYPE_BOOL)
            is VmType.Tuple -> {
                out.writeByte(TYPE_TUPLE)
                out.writeByte(t.elementTypes.size)
                t.elementTypes.forEach { writeType(it, out) }
            }
            is VmType.Array -> {
                out.writeByte(TYPE_ARRAY)
                writeType(t.elementType, out)
            }
            is VmType.Class -> {
                out.writeByte(TYPE_CLASS)
                out.writeUTF(t.name)
            }
            is VmType.Func -> {
                out.writeByte(TYPE_FUNC)
                val args = t.args
                out.writeBoolean(args != null)
                if (args != null) {
                    out.writeByte(args.size)
                    args.forEach { writeType(it, out) }
                }
                val ret = t.ret
                out.writeBoolean(ret != null)
                if (ret != null) {
                    writeType(ret, out)
                }
            }
            is VmType.Ptr -> {
                out.writeByte(TYPE_PTR)
                writeType(t.derived, out)
            }
        }
    }

    // ---- Reading ----

    fun read(file: File): SerializedProgram {
        return file.inputStream().use { fis ->
            DataInputStream(fis.buffered()).use { inp -> read(inp) }
        }
    }

    fun read(inp: DataInputStream): SerializedProgram {
        val magic = inp.readShort().toInt()
        if (magic != MAGIC) {
            throw IllegalArgumentException("Not a Velo bytecode file, wrong magic: $magic")
        }
        val versionMajor = inp.readByte().toInt()
        val versionMinor = inp.readByte().toInt()
        if (versionMajor != VERSION_MAJOR) {
            throw IllegalArgumentException("Unsupported bytecode version: $versionMajor.$versionMinor")
        }
        val natives = readNatives(inp)
        val dataClasses = readDataClasses(inp)
        val frames = List(inp.readShort().toInt()) { readFrame(inp) }
        return SerializedProgram(natives = natives, frames = frames, dataClasses = dataClasses)
    }

    private fun readDataClasses(inp: DataInputStream): List<DataClassInfo> {
        return List(inp.readShort().toInt()) {
            val frameNum = inp.readInt()
            val name = inp.readUTF()
            val fields = List(inp.readByte().toInt()) {
                DataField(name = inp.readUTF(), index = inp.readInt(), type = readType(inp))
            }
            DataClassInfo(frameNum = frameNum, name = name, fields = fields)
        }
    }

    private fun readNatives(inp: DataInputStream): List<NativeRef> {
        return List(inp.readShort().toInt()) {
            val kind = if (inp.readByte().toInt() == 0) NativeRef.Kind.CONSTRUCTOR else NativeRef.Kind.METHOD
            val className = inp.readUTF()
            val methodName = inp.readUTF()
            val params = List(inp.readByte().toInt()) { readType(inp) }
            val returns = readType(inp)
            NativeRef(kind, className, methodName, params, returns)
        }
    }

    private fun readFrame(inp: DataInputStream): SerializedFrame {
        val num = inp.readShort().toInt()
        val vars = List(inp.readShort().toInt()) { inp.readShort().toInt() }
        val ops = List(inp.readShort().toInt()) { readOp(inp) }
        return SerializedFrame(num = num, ops = ops, vars = vars)
    }

    private fun readOp(inp: DataInputStream): Op {
        return when (val opcode = inp.readByte().toInt()) {
            0x02 -> Op.And
            0x03 -> Op.ArrNew
            0x04 -> Op.ArrLoad
            0x05 -> Op.ArrLen
            0x08 -> Op.ArrStore
            0x09 -> Op.Call(
                args = inp.readInt(),
                classParent = inp.readBoolean(),
                callbackResult = inp.readBoolean(),
            )
            0x0b -> Op.Div
            0x0c -> Op.Pop
            0x0d -> Op.Dup
            0x0e -> Op.Equals
            0x0f -> Op.Load(index = inp.readInt())
            0x11 -> Op.Halt
            0x12 -> Op.If(elseSkip = inp.readInt())
            0x14 -> Op.IntChar
            0x15 -> Op.IntStr
            0x18 -> Op.Frame(num = inp.readInt())
            0x1a -> Op.Sub
            0x1b -> Op.More
            0x1d -> Op.Move(count = inp.readInt())
            0x1e -> Op.Mul
            0x21 -> Op.Or
            0x26 -> Op.Add
            0x29 -> Op.Push(value = readValue(inp))
            0x2a -> Op.Rem
            0x2b -> Op.Ret
            0x2c -> Op.Rot
            0x2d -> Op.Store(index = inp.readInt())
            0x2e -> Op.StrCon
            0x2f -> Op.StrIndex
            0x30 -> Op.StrLen
            0x32 -> Op.ArrCopy
            0x33 -> Op.StrSub
            0x34 -> Op.Swap
            0x35 -> Op.Xor
            0x40 -> Op.StrInt
            0x42 -> Op.Instance
            0x43 -> Op.NativeCall(
                poolIndex = inp.readShort().toInt(),
                args = List(inp.readByte().toInt()) { readType(inp) },
            )
            0x46 -> Op.Shl
            0x47 -> Op.Shr
            0x48 -> Op.Hash
            0x50 -> Op.PtrNew
            0x51 -> Op.PtrLoad
            0x52 -> Op.PtrStore
            0x53 -> Op.PtrRef(varIndex = inp.readInt())
            0x54 -> Op.PtrRefIndex
            0x60 -> Op.ActorSpawn(
                classFrameNum = inp.readInt(),
                className = inp.readUTF(),
                args = inp.readInt(),
            )
            0x61 -> Op.ActorCall(
                methodVarIndex = inp.readInt(),
                args = inp.readInt(),
            )
            0x62 -> Op.FutureAwait
            else -> throw IllegalStateException("Unsupported opcode: $opcode")
        }
    }

    private fun readValue(inp: DataInputStream): Any {
        return when (val tag = inp.readByte().toInt()) {
            TYPE_BYTE -> inp.readByte()
            TYPE_INT -> inp.readInt()
            TYPE_FLOAT -> inp.readFloat()
            TYPE_STR -> inp.readUTF()
            TYPE_BOOL -> inp.readBoolean()
            TYPE_NULL_PTR -> NullPtr
            else -> throw IllegalStateException("Unsupported literal tag: $tag")
        }
    }

    private fun readType(inp: DataInputStream): VmType {
        return when (val tag = inp.readByte().toInt()) {
            TYPE_VOID -> VmType.Void
            TYPE_ANY -> VmType.Any
            TYPE_BYTE -> VmType.Byte
            TYPE_INT -> VmType.Int
            TYPE_FLOAT -> VmType.Float
            TYPE_STR -> VmType.Str
            TYPE_BOOL -> VmType.Bool
            TYPE_TUPLE -> VmType.Tuple(List(inp.readByte().toInt()) { readType(inp) })
            TYPE_ARRAY -> VmType.Array(elementType = readType(inp))
            TYPE_CLASS -> VmType.Class(name = inp.readUTF())
            TYPE_FUNC -> {
                val args = if (inp.readBoolean()) {
                    List(inp.readByte().toInt()) { readType(inp) }
                } else {
                    null
                }
                val ret = if (inp.readBoolean()) readType(inp) else null
                VmType.Func(args = args, ret = ret)
            }
            TYPE_PTR -> VmType.Ptr(derived = readType(inp))
            else -> throw IllegalStateException("Unsupported type tag: $tag")
        }
    }
}
