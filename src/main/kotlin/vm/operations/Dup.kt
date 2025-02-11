package vm.operations

import vm.Frame
import vm.SimpleOperation

class Dup: SimpleOperation {

    override fun exec(frame: Frame) {
        val rec = frame.subs.peek()
        frame.subs.push(rec)
    }

}