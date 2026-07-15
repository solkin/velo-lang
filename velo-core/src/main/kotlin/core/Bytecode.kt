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
    // v13: frame/slot references narrowed to u16 to match their u16 containers
    // (frame count, per-frame var count). Jump offsets and arities stay i32.
    // v13.1: added the try/catch/throw opcodes (TryEnter/TryLeave/Throw).
    const val VERSION_MAJOR = 0x0d
    const val VERSION_MINOR = 0x01

    // Tags for inline values and serialized VmTypes.
    // 0x09 was TYPE_DICT — retired in v10 along with the dict opcodes.
    private const val TYPE_VOID = 0x00
    private const val TYPE_ANY = 0x01
    private const val TYPE_BYTE = 0x02
    private const val TYPE_INT = 0x03
    private const val TYPE_FLOAT = 0x04
    private const val TYPE_LONG = 0x0e
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
        writeClassMethods(program.classMethods, out)
        // Frame and slot references are u16 (see version note); fail loudly rather
        // than let writeShort silently truncate a program past the 65535 ceiling.
        require(program.frames.size <= 0xFFFF) {
            "program has ${program.frames.size} frames, exceeds the 65535 the .vbc format allows"
        }
        out.writeShort(program.frames.size)
        program.frames.forEach { writeFrame(it, out) }
    }

    private fun writeClassMethods(classMethods: List<ClassMethodsInfo>, out: DataOutputStream) {
        out.writeShort(classMethods.size)
        classMethods.forEach { info ->
            out.writeShort(info.frameNum)
            out.writeShort(info.methods.size)
            info.methods.forEach { method ->
                out.writeUTF(method.name)
                out.writeShort(method.index)
            }
        }
    }

    private fun writeDataClasses(dataClasses: List<DataClassInfo>, out: DataOutputStream) {
        out.writeShort(dataClasses.size)
        dataClasses.forEach { info ->
            out.writeShort(info.frameNum)
            out.writeUTF(info.name)
            out.writeByte(info.fields.size)
            info.fields.forEach { field ->
                out.writeUTF(field.name)
                out.writeShort(field.index)
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
        require(frame.vars.size <= 0xFFFF) {
            "frame ${frame.num} declares ${frame.vars.size} slots, exceeds the 65535 the .vbc format allows"
        }
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
            is Op.Load -> out.writeShort(op.index)
            is Op.Store -> out.writeShort(op.index)
            is Op.If -> out.writeInt(op.elseSkip)
            is Op.Move -> out.writeInt(op.count)
            is Op.TryEnter -> out.writeInt(op.catchOffset)
            is Op.ScopeEnter -> { out.writeShort(op.base); out.writeShort(op.count) }
            is Op.Frame -> out.writeShort(op.num)
            is Op.MethodLoad -> out.writeUTF(op.name)
            is Op.InterfaceCall -> {
                out.writeUTF(op.method)
                out.writeInt(op.args)
            }
            is Op.PtrRef -> out.writeShort(op.varIndex)
            is Op.Call -> {
                out.writeInt(op.args)
                out.writeBoolean(op.classParent)
                out.writeBoolean(op.callbackResult)
                out.writeBoolean(op.reverseArgs)
            }
            is Op.NativeCall -> {
                out.writeShort(op.poolIndex)
                out.writeByte(op.args.size)
                op.args.forEach { writeType(it, out) }
            }
            is Op.ActorSpawn -> {
                out.writeShort(op.classFrameNum)
                out.writeUTF(op.className)
                out.writeInt(op.args)
            }
            is Op.ActorCall -> {
                out.writeShort(op.methodVarIndex)
                out.writeInt(op.args)
            }
            is Op.NumConv -> writeType(op.to, out)
            is Op.StrNum -> writeType(op.to, out)
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
            is Long -> {
                out.writeByte(TYPE_LONG)
                out.writeLong(value)
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
            is VmType.Long -> out.writeByte(TYPE_LONG)
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
        val magic = inp.readUnsignedShort()
        if (magic != MAGIC) {
            throw IllegalArgumentException("Not a Velo bytecode file, wrong magic: $magic")
        }
        val versionMajor = inp.readUnsignedByte()
        val versionMinor = inp.readUnsignedByte()
        if (versionMajor != VERSION_MAJOR) {
            throw IllegalArgumentException("Unsupported bytecode version: $versionMajor.$versionMinor")
        }
        val natives = readNatives(inp)
        val dataClasses = readDataClasses(inp)
        val classMethods = readClassMethods(inp)
        val frames = List(inp.readUnsignedShort()) { readFrame(inp) }
        return SerializedProgram(
            natives = natives,
            frames = frames,
            dataClasses = dataClasses,
            classMethods = classMethods,
        )
    }

    private fun readClassMethods(inp: DataInputStream): List<ClassMethodsInfo> {
        return List(inp.readUnsignedShort()) {
            val frameNum = inp.readUnsignedShort()
            val methods = List(inp.readUnsignedShort()) {
                ClassMethod(name = inp.readUTF(), index = inp.readUnsignedShort())
            }
            ClassMethodsInfo(frameNum = frameNum, methods = methods)
        }
    }

    private fun readDataClasses(inp: DataInputStream): List<DataClassInfo> {
        return List(inp.readUnsignedShort()) {
            val frameNum = inp.readUnsignedShort()
            val name = inp.readUTF()
            val fields = List(inp.readUnsignedByte()) {
                DataField(name = inp.readUTF(), index = inp.readUnsignedShort(), type = readType(inp))
            }
            DataClassInfo(frameNum = frameNum, name = name, fields = fields)
        }
    }

    private fun readNatives(inp: DataInputStream): List<NativeRef> {
        return List(inp.readUnsignedShort()) {
            val kind = when (val tag = inp.readUnsignedByte()) {
                0 -> NativeRef.Kind.CONSTRUCTOR
                1 -> NativeRef.Kind.METHOD
                else -> throw IllegalStateException("Unsupported native kind: $tag")
            }
            val className = inp.readUTF()
            val methodName = inp.readUTF()
            val params = List(inp.readUnsignedByte()) { readType(inp) }
            val returns = readType(inp)
            NativeRef(kind, className, methodName, params, returns)
        }
    }

    private fun readFrame(inp: DataInputStream): SerializedFrame {
        val num = inp.readUnsignedShort()
        val vars = List(inp.readUnsignedShort()) { inp.readUnsignedShort() }
        val ops = List(inp.readUnsignedShort()) { readOp(inp) }
        return SerializedFrame(num = num, ops = ops, vars = vars)
    }

    private fun readOp(inp: DataInputStream): Op {
        return when (val opcode = inp.readUnsignedByte()) {
            0x02 -> Op.And
            0x03 -> Op.ArrNew
            0x04 -> Op.ArrLoad
            0x05 -> Op.ArrLen
            0x08 -> Op.ArrStore
            0x09 -> Op.Call(
                args = inp.readInt(),
                classParent = inp.readBoolean(),
                callbackResult = inp.readBoolean(),
                reverseArgs = inp.readBoolean(),
            )
            0x0b -> Op.Div
            0x0c -> Op.Pop
            0x0d -> Op.Dup
            0x0e -> Op.Equals
            0x0f -> Op.Load(index = inp.readUnsignedShort())
            0x11 -> Op.Halt
            0x12 -> Op.If(elseSkip = inp.readInt())
            0x14 -> Op.IntChar
            0x63 -> Op.NumConv(to = readType(inp))
            0x64 -> Op.NumStr
            0x65 -> Op.StrNum(to = readType(inp))
            0x18 -> Op.Frame(num = inp.readUnsignedShort())
            0x19 -> Op.MethodLoad(name = inp.readUTF())
            0x1c -> Op.InterfaceCall(method = inp.readUTF(), args = inp.readInt())
            0x1a -> Op.Sub
            0x1b -> Op.More
            0x1d -> Op.Move(count = inp.readInt())
            0x22 -> Op.ScopeEnter(base = inp.readUnsignedShort(), count = inp.readUnsignedShort())
            0x23 -> Op.ScopeLeave
            0x24 -> Op.TryEnter(catchOffset = inp.readInt())
            0x25 -> Op.TryLeave
            0x27 -> Op.Throw
            0x1e -> Op.Mul
            0x21 -> Op.Or
            0x26 -> Op.Add
            0x29 -> Op.Push(value = readValue(inp))
            0x2a -> Op.Rem
            0x2b -> Op.Ret
            0x2d -> Op.Store(index = inp.readUnsignedShort())
            0x2e -> Op.StrCon
            0x2f -> Op.StrIndex
            0x30 -> Op.StrLen
            0x32 -> Op.ArrCopy
            0x33 -> Op.StrSub
            0x34 -> Op.Swap
            0x35 -> Op.Xor
            0x42 -> Op.Instance
            0x43 -> Op.NativeCall(
                poolIndex = inp.readUnsignedShort(),
                args = List(inp.readUnsignedByte()) { readType(inp) },
            )
            0x46 -> Op.Shl
            0x47 -> Op.Shr
            0x66 -> Op.Ushr
            0x48 -> Op.Hash
            0x50 -> Op.PtrNew
            0x51 -> Op.PtrLoad
            0x52 -> Op.PtrStore
            0x53 -> Op.PtrRef(varIndex = inp.readUnsignedShort())
            0x54 -> Op.PtrRefIndex
            0x60 -> Op.ActorSpawn(
                classFrameNum = inp.readUnsignedShort(),
                className = inp.readUTF(),
                args = inp.readInt(),
            )
            0x61 -> Op.ActorCall(
                methodVarIndex = inp.readUnsignedShort(),
                args = inp.readInt(),
            )
            0x62 -> Op.FutureAwait
            else -> throw IllegalStateException("Unsupported opcode: $opcode")
        }
    }

    private fun readValue(inp: DataInputStream): Any {
        return when (val tag = inp.readUnsignedByte()) {
            TYPE_BYTE -> inp.readByte()
            TYPE_INT -> inp.readInt()
            TYPE_LONG -> inp.readLong()
            TYPE_FLOAT -> inp.readFloat()
            TYPE_STR -> inp.readUTF()
            TYPE_BOOL -> inp.readBoolean()
            TYPE_NULL_PTR -> NullPtr
            else -> throw IllegalStateException("Unsupported literal tag: $tag")
        }
    }

    private fun readType(inp: DataInputStream): VmType {
        return when (val tag = inp.readUnsignedByte()) {
            TYPE_VOID -> VmType.Void
            TYPE_ANY -> VmType.Any
            TYPE_BYTE -> VmType.Byte
            TYPE_INT -> VmType.Int
            TYPE_LONG -> VmType.Long
            TYPE_FLOAT -> VmType.Float
            TYPE_STR -> VmType.Str
            TYPE_BOOL -> VmType.Bool
            TYPE_TUPLE -> VmType.Tuple(List(inp.readUnsignedByte()) { readType(inp) })
            TYPE_ARRAY -> VmType.Array(elementType = readType(inp))
            TYPE_CLASS -> VmType.Class(name = inp.readUTF())
            TYPE_FUNC -> {
                val args = if (inp.readBoolean()) {
                    List(inp.readUnsignedByte()) { readType(inp) }
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
