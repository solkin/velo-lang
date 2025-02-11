package vm.operations

import vm.Frame
import vm.HaltException
import vm.SimpleOperation

class Halt: SimpleOperation {

    override fun exec(frame: Frame) {
        throw HaltException()
    }

}