package vm.operations

import vm.Frame
import vm.FrameLoader
import vm.Operation
import vm.Stack
import vm.VmType
import vm.records.NativeRecord
import kotlin.Pair
import kotlin.collections.List
import kotlin.collections.map
import kotlin.collections.toTypedArray

class NativeConstructor(val name: String, val args: List<Pair<Int, VmType>>) : Operation {

    override fun exec(pc: Int, stack: Stack<Frame>, frameLoader: FrameLoader): Int {
        val frame = stack.peek()
        try {
            val clazz = Class.forName(name)

            val argTypes = args.map { Pair(it.first, it.second.toJvmType()) }
            val constructor = clazz.getConstructor(*argTypes.map { it.second }.toTypedArray())

            val instance = constructor.newInstance(*args.map { arg ->
                frame.vars.get(arg.first).getAs(vmType = arg.second)
            }.toTypedArray())

            val result = NativeRecord.create(instance)
            frame.subs.push(result)
        } catch (ex: NoSuchMethodException) {
            throw Exception("Unable to create native class $name instance: ${ex.message}")
        }
        return pc + 1
    }

}