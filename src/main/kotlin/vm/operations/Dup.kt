package vm.operations

import vm.VMContext

import vm.Frame
import vm.SimpleOperation

class Dup: SimpleOperation {

    override fun exec(frame: Frame, ctx: VMContext) {
        val rec = frame.subs.peek()
        frame.subs.push(rec)
    }

}