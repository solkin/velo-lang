package vm.operations

import vm.Frame
import vm.SimpleOperation

class PairFirst : SimpleOperation {

    override fun exec(frame: Frame) {
        val pair = frame.subs.pop().getPair()

        frame.subs.push(pair.first)
    }

}
