package vm.operations

import vm.Frame
import vm.SimpleOperation

class Swap: SimpleOperation {

    override fun exec(frame: Frame) {
        with(frame.subs) {
            val rec1 = pop()
            val rec2 = pop()
            push(rec1)
            push(rec2)
        }
    }

}