package vm.operations

import vm.Frame
import vm.SimpleOperation

class Print : SimpleOperation {

    override fun exec(frame: Frame) {
        val value = frame.subs.pop().get()
        print(value.toString())
    }

}