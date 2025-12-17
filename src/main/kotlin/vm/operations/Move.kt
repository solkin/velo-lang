package vm.operations

import vm.Operation
import vm.VMContext

class Move(
    val count: Int
) : Operation {

    override fun exec(pc: Int, ctx: VMContext): Int {
        return pc + count + 1
    }

}