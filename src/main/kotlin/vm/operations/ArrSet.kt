package vm.operations

import vm.Frame
import vm.SimpleOperation

class ArrSet : SimpleOperation {

    override fun exec(frame: Frame) {
        val index = frame.subs.pop().getInt()
        val array = frame.subs.pop().getArray()
        val value = frame.subs.pop()

        array[index] = value
    }

}