package vm.operations

import vm.Frame
import vm.FrameLoader
import vm.Operation
import vm.Stack
import vm.records.LinkRecord
import vm.toType
import java.lang.reflect.Method
import kotlin.Pair
import kotlin.collections.List
import kotlin.collections.map
import kotlin.collections.toTypedArray

class NativeInvoke(val args: List<Pair<Int, Byte>>) : Operation {

    override fun exec(pc: Int, stack: Stack<Frame>, frameLoader: FrameLoader): Int {
        val frame = stack.peek()

        val instance = frame.subs.pop().get<Any>()
        val method = frame.subs.pop().get<Method>()

        try {
            val nativeResult = method.invoke(instance, *args.map { arg ->
                frame.vars.get(arg.first).toType(vmType = arg.second)
            }.toTypedArray())

            nativeResult?.let {
                val result = LinkRecord.create(nativeResult)
                frame.subs.push(result)
            }
        } catch (ex: NoSuchMethodException) {
            throw Exception("Unable to invoke native method ${method.name}: ${ex.message}")
        }

        return pc + 1
    }

}