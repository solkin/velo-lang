package vm.operations

import vm.Frame
import vm.SimpleOperation

class Rot: SimpleOperation {

    override fun exec(frame: Frame) {
        with(frame.subs) {
            val rec1 = pop()
            val rec2 = pop()
            val rec3 = pop()
            push(rec2)
            push(rec1)
            push(rec3)
        }
    }

}