package vm.operations

import vm.Frame
import vm.FrameLoader
import vm.Operation
import vm.Stack
import vm.records.LinkRecord
import vm.toJvmType
import kotlin.collections.List
import kotlin.collections.map
import kotlin.collections.toTypedArray

class NativeFunction(val name: String, val argTypes: List<Byte>) : Operation {

    override fun exec(pc: Int, stack: Stack<Frame>, frameLoader: FrameLoader): Int {
        val frame = stack.peek()

        val instance = frame.subs.pop().get<Any>()

        val clazz: Class<*> = instance::class.java

        try {
            val method = clazz.getMethod(name, *argTypes.map { it.toJvmType() }.toTypedArray())

            val result = LinkRecord.create(method)
            frame.subs.push(result)
        } catch (ex: NoSuchMethodException) {
            throw Exception("Unable to find native method $name: ${ex.message}")
        }

        return pc + 1
    }

}