package vm.operations

import vm.VMContext

import vm.Frame
import vm.SimpleOperation

class Swap: SimpleOperation {

    override fun exec(frame: Frame, ctx: VMContext) {
        with(frame.subs) {
            val rec1 = pop()
            val rec2 = pop()
            push(rec1)
            push(rec2)
        }
    }

}