package vm.operations

import vm.Frame
import vm.SimpleOperation

class ArrSet : SimpleOperation {

    override fun exec(frame: Frame) {
        val index = frame.subs.pop().getInt()
        val value = frame.subs.pop()
        val array = frame.subs.pop().getArray()

        array[index] = value
    }

}