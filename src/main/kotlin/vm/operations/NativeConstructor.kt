package vm.operations

import vm.Frame
import vm.FrameLoader
import vm.Operation
import vm.Stack
import vm.records.LinkRecord
import vm.toJvmType
import vm.toType
import kotlin.Pair
import kotlin.collections.List
import kotlin.collections.map
import kotlin.collections.toTypedArray

class NativeConstructor(val name: String, val args: List<Pair<Int, Byte>>) : Operation {

    override fun exec(pc: Int, stack: Stack<Frame>, frameLoader: FrameLoader): Int {
        val frame = stack.peek()
        try {
            val clazz = Class.forName(name)

            val argTypes = args.map { Pair(it.first, it.second.toJvmType()) }
            val constructor = clazz.getConstructor(*argTypes.map { it.second }.toTypedArray())

            val instance = constructor.newInstance(*args.map { arg ->
                frame.vars.get(arg.first).toType(vmType = arg.second)
            }.toTypedArray())

            val result = LinkRecord.create(instance)
            frame.subs.push(result)
        } catch (ex: NoSuchMethodException) {
            throw Exception("Unable to create native class $name instance: ${ex.message}")
        }
        return pc + 1
    }

}