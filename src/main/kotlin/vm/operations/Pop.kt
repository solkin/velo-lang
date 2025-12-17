package vm.operations

import vm.VMContext

import vm.Frame
import vm.SimpleOperation

class Pop: SimpleOperation {

    override fun exec(frame: Frame, ctx: VMContext) {
        frame.subs.pop()
    }

}