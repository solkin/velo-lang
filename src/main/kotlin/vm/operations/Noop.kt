package vm.operations

import vm.Operation
import vm.VMContext

class Noop : Operation {

    override fun exec(pc: Int, ctx: VMContext): Int {
        return pc + 1
    }

}