package vm.operations

import vm.Operation
import vm.VMContext
import vm.VmType
import vm.records.RefRecord

class NativeFunction(val name: String, val argTypes: List<VmType>) : Operation {

    override fun exec(pc: Int, ctx: VMContext): Int {
        val frame = ctx.currentFrame()

        val instance = frame.subs.pop().get<Any>()

        val clazz: Class<*> = instance::class.java

        try {
            val method = clazz.getMethod(name, *argTypes.map { it.toJvmType() }.toTypedArray())

            val result = RefRecord.native(method, ctx)
            frame.subs.push(result)
        } catch (ex: NoSuchMethodException) {
            throw Exception("Unable to find native method $name: ${ex.message}")
        }

        return pc + 1
    }

}
