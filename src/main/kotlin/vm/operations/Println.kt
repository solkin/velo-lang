package vm.operations

import vm.Frame
import vm.SimpleOperation

class Println : SimpleOperation {

    override fun exec(frame: Frame) {
        val value = frame.subs.pop().get()
        println(value.toString())
    }

}