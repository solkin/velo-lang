package vm.operations

import vm.VMContext

import vm.Frame
import vm.HaltException
import vm.SimpleOperation

class Halt: SimpleOperation {

    override fun exec(frame: Frame, ctx: VMContext) {
        throw HaltException()
    }

}