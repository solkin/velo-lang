package vm.operations

import vm.NativeBridge
import vm.Operation
import vm.VMContext
import vm.VmType
import vm.records.RefRecord

/**
 * Invoke one entry of the program's native pool — the only opcode of the
 * native interop.
 *
 * Stack layout (top to bottom) on entry:
 *   1. arg_1, ..., arg_n   — arguments, first argument on top
 *   2. receiver            — native instance, only for method entries
 *
 * [poolIndex] selects the [vm.BoundNative] linked at load time; the call is
 * a direct [java.lang.invoke.MethodHandle] invocation, no reflection lookup
 * happens here. [args] carries the call-site Velo types used to convert
 * each argument — for callback parameters this is the actual function
 * signature, which arms the host-side [vm.actors.VeloFunction] validation.
 *
 * Constructor entries push a fresh native instance record; method entries
 * push the converted result, or nothing for void methods.
 */
class NativeCall(
    val poolIndex: Int,
    val args: List<VmType>,
) : Operation {

    override fun exec(pc: Int, ctx: VMContext): Int {
        val bound = ctx.natives.getOrNull(poolIndex)
            ?: throw IllegalStateException("Native pool entry #$poolIndex is not linked")
        val frame = ctx.currentFrame()

        val jvmArgs = ArrayList<Any?>(args.size + 1)
        if (!bound.isConstructor) {
            jvmArgs.add(null) // placeholder for the receiver, filled after args are popped
        }
        for (i in args.indices) {
            val record = frame.subs.pop()
            jvmArgs.add(NativeBridge.toJvm(record, args[i], bound.jvmParams[i], ctx))
        }
        if (!bound.isConstructor) {
            val receiver = frame.subs.pop()
            jvmArgs[0] = NativeBridge.toJvm(receiver, VmType.Class(bound.ref.className), bound.handle.type().parameterType(0), ctx)
        }

        val result = try {
            bound.handle.invokeWithArguments(jvmArgs)
        } catch (ex: Throwable) {
            throw RuntimeException("Native call ${bound.ref} failed: ${ex.message ?: ex}", ex)
        }

        if (bound.isConstructor) {
            frame.subs.push(RefRecord.native(result!!, ctx))
        } else {
            NativeBridge.toVelo(result, ctx)?.let { frame.subs.push(it) }
        }
        return pc + 1
    }
}
