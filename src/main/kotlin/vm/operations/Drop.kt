package vm.operations

import vm.Frame
import vm.SimpleOperation

class Drop: SimpleOperation {

    override fun exec(frame: Frame) {
        frame.subs.pop()
    }

}