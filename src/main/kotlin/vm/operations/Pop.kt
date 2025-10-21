package vm.operations

import vm.Frame
import vm.SimpleOperation

class Pop: SimpleOperation {

    override fun exec(frame: Frame) {
        frame.subs.pop()
    }

}