package vm.operations

import vm.Operation
import vm.VMContext

class Ret : Operation {

    override fun exec(pc: Int, ctx: VMContext): Int {
        val frame = ctx.popFrame()
        if (!frame.subs.empty()) {
            val value = frame.subs.pop()
            ctx.currentFrame().subs.push(value)
        }
        return pc // This is program counter value for the dropped frame, the VM will not use it
    }

}