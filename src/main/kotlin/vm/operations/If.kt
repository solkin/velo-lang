package vm.operations

import vm.Operation
import vm.VMContext

class If(
    val elseSkip: Int
) : Operation {

    override fun exec(pc: Int, ctx: VMContext): Int {
        val flag = ctx.currentFrame().subs.pop().getBool()
        return pc + 1 + if (flag) 0 else elseSkip
    }

}