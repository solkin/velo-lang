package vm.operations

import vm.Operation
import vm.VMContext
import vm.VmType
import vm.records.LinkRecord
import java.lang.reflect.Method

class NativeInvoke(val args: List<Pair<Int, VmType>>) : Operation {

    override fun exec(pc: Int, ctx: VMContext): Int {
        val frame = ctx.currentFrame()

        val instance = frame.subs.pop().get<Any>()
        val method = frame.subs.pop().get<Method>()

        try {
            val nativeResult = method.invoke(instance, *args.map { arg ->
                val record = frame.vars.get(arg.first)
                record.getAs(vmType = arg.second, ctx = ctx)
            }.toTypedArray())

            nativeResult?.let {
                val result = LinkRecord.create(nativeResult, ctx)
                frame.subs.push(result)
            }
        } catch (ex: NoSuchMethodException) {
            throw Exception("Unable to invoke native method ${method.name}: ${ex.message}")
        }

        return pc + 1
    }

}