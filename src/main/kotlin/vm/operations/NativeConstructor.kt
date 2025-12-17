package vm.operations

import vm.Operation
import vm.VMContext
import vm.VmType
import vm.records.NativeRecord

class NativeConstructor(val name: String, val args: List<Pair<Int, VmType>>) : Operation {

    override fun exec(pc: Int, ctx: VMContext): Int {
        val frame = ctx.currentFrame()
        try {
            // Try to get class from registry first, then fall back to Class.forName
            val classInfo = ctx.nativeRegistry.getByVeloName(name)
            val clazz = classInfo?.jvmClass ?: Class.forName(name)

            val argTypes = args.map { Pair(it.first, it.second.toJvmType()) }
            val constructor = clazz.getConstructor(*argTypes.map { it.second }.toTypedArray())

            val instance = constructor.newInstance(*args.map { arg ->
                frame.vars.get(arg.first).getAs(vmType = arg.second, ctx = ctx)
            }.toTypedArray())

            val result = NativeRecord.create(instance, ctx)
            frame.subs.push(result)
        } catch (ex: NoSuchMethodException) {
            throw Exception("Unable to create native class $name instance: ${ex.message}")
        }
        return pc + 1
    }

}