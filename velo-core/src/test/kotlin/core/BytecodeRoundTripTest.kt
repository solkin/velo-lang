package core

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import kotlin.reflect.full.createInstance
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * The instruction set's single-source-of-truth guarantee, enforced:
 * every [Op] must survive write → read unchanged, and every Op the
 * language gains in the future must show up here or the completeness
 * check fails. This is what catches a writer/reader branch going missing
 * or drifting out of sync.
 */
class BytecodeRoundTripTest {

    /**
     * One representative instance per operation. Ops are data classes /
     * objects, so equality after the round trip is structural.
     */
    private val samples: List<Op> = listOf(
        Op.Push(42),
        Op.Push(4_294_967_296L),
        Op.Push(3.14f),
        Op.Push("hello"),
        Op.Push(true),
        Op.Push(7.toByte()),
        Op.Push(NullPtr),
        Op.Pop,
        Op.Dup,
        Op.Swap,
        Op.Rot,
        Op.Add,
        Op.Sub,
        Op.Mul,
        Op.Div,
        Op.Rem,
        Op.And,
        Op.Or,
        Op.Xor,
        Op.Shl,
        Op.Shr,
        Op.More,
        Op.Equals,
        Op.ScopeEnter(base = 3, count = 2),
        Op.ScopeLeave,
        Op.IntChar,
        Op.IntStr,
        Op.FloatStr,
        Op.StrInt,
        Op.Hash,
        Op.IntToFloat,
        Op.FloatToInt,
        Op.IntToByte,
        Op.IntToLong,
        Op.LongToInt,
        Op.LongToFloat,
        Op.FloatToLong,
        Op.LongStr,
        Op.StrCon,
        Op.StrLen,
        Op.StrIndex,
        Op.StrSub,
        Op.ArrNew,
        Op.ArrLen,
        Op.ArrLoad,
        Op.ArrStore,
        Op.ArrCopy,
        Op.Load(index = 3),
        Op.Store(index = 4),
        Op.If(elseSkip = 2),
        Op.Move(count = 5),
        Op.Halt,
        Op.Ret,
        Op.Frame(num = 7),
        Op.MethodLoad(name = "padding"),
        Op.InterfaceCall(method = "padding", args = 1),
        Op.Instance,
        Op.Call(args = 2, classParent = true),
        Op.Call(args = -1, classParent = false),
        Op.Call(args = 1, classParent = false, callbackResult = true),
        Op.PtrNew,
        Op.PtrLoad,
        Op.PtrStore,
        Op.PtrRef(varIndex = 6),
        Op.PtrRefIndex,
        Op.NativeCall(
            poolIndex = 1,
            args = listOf(
                VmType.Int,
                VmType.Str,
                VmType.Array(VmType.Float),
                VmType.Tuple(listOf(VmType.Bool, VmType.Byte)),
                VmType.Func(args = listOf(VmType.Int), ret = VmType.Void),
                VmType.Func(args = null, ret = null),
                VmType.Class("Terminal"),
                VmType.Ptr(VmType.Int),
            ),
        ),
        Op.ActorSpawn(classFrameNum = 2, className = "Worker", args = 1),
        Op.ActorCall(methodVarIndex = 3, args = 2),
        Op.FutureAwait,
    )

    @Test
    fun `every op round-trips through bytecode unchanged`() {
        val program = SerializedProgram(
            natives = listOf(
                NativeRef(
                    kind = NativeRef.Kind.CONSTRUCTOR,
                    className = "Terminal",
                    methodName = "",
                    params = emptyList(),
                    returns = VmType.Class("Terminal"),
                ),
                NativeRef(
                    kind = NativeRef.Kind.METHOD,
                    className = "Terminal",
                    methodName = "println",
                    params = listOf(VmType.Str),
                    returns = VmType.Void,
                ),
            ),
            frames = listOf(
                SerializedFrame(num = 0, ops = samples, vars = listOf(0, 1, 2)),
                SerializedFrame(num = 1, ops = listOf(Op.Ret), vars = emptyList()),
            ),
            dataClasses = listOf(
                DataClassInfo(
                    frameNum = 1,
                    name = "Point",
                    fields = listOf(
                        DataField(name = "x", index = 2, type = VmType.Int),
                        DataField(name = "y", index = 3, type = VmType.Int),
                    ),
                ),
                DataClassInfo(
                    frameNum = 5,
                    name = "Segment",
                    fields = listOf(
                        DataField(name = "label", index = 0, type = VmType.Str),
                        DataField(name = "points", index = 1, type = VmType.Array(VmType.Class("Point"))),
                    ),
                ),
            ),
            classMethods = listOf(
                ClassMethodsInfo(
                    frameNum = 1,
                    methods = listOf(
                        ClassMethod(name = "padding", index = 3),
                        ClassMethod(name = "visible", index = 4),
                    ),
                ),
                ClassMethodsInfo(frameNum = 5, methods = emptyList()),
            ),
        )

        val bytes = ByteArrayOutputStream().use { baos ->
            DataOutputStream(baos).use { out -> Bytecode.write(program, out) }
            baos.toByteArray()
        }
        val reread = DataInputStream(ByteArrayInputStream(bytes)).use { inp ->
            Bytecode.read(inp)
        }

        assertEquals(program, reread)
    }

    @Test
    fun `sample list covers the whole instruction set`() {
        val sampled = samples.map { it::class }.toSet()
        val missing = Op::class.sealedSubclasses.filterNot { it in sampled }
        assertTrue(
            missing.isEmpty(),
            "Ops without a round-trip sample (add them to `samples`): " +
                missing.joinToString { it.simpleName ?: "?" }
        )
    }

    @Test
    fun `opcodes are unique across the instruction set`() {
        val byOpcode = HashMap<Int, String>()
        for (subclass in Op::class.sealedSubclasses) {
            val instance = subclass.objectInstance
                ?: samples.firstOrNull { it::class == subclass }
                ?: subclass.createInstance()
            val name = subclass.simpleName ?: "?"
            val previous = byOpcode.put(instance.opcode, name)
            if (previous != null) {
                fail("Opcode 0x%02x is used by both %s and %s".format(instance.opcode, previous, name))
            }
        }
        assertEquals(Op::class.sealedSubclasses.size, byOpcode.size)
    }

    @Test
    fun `unsigned u8 and u16 fields retain their full wire range`() {
        val manyTypes = List(128) { VmType.Int }
        val program = SerializedProgram(
            natives = listOf(
                NativeRef(NativeRef.Kind.METHOD, "Wide", "call", manyTypes, VmType.Void),
            ),
            frames = listOf(
                SerializedFrame(
                    num = 65_535,
                    vars = listOf(65_535),
                    ops = listOf(Op.NativeCall(poolIndex = 65_535, args = manyTypes)),
                ),
            ),
        )

        assertEquals(program, roundTrip(program))
    }

    private fun roundTrip(program: SerializedProgram): SerializedProgram {
        val bytes = ByteArrayOutputStream().use { baos ->
            DataOutputStream(baos).use { Bytecode.write(program, it) }
            baos.toByteArray()
        }
        return DataInputStream(ByteArrayInputStream(bytes)).use { Bytecode.read(it) }
    }
}
