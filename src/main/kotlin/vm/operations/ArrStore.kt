package vm.operations

import vm.Frame
import vm.SimpleOperation

class ArrStore : SimpleOperation {

    override fun exec(frame: Frame) {
        val count = frame.subs.pop().getInt()
        val index = frame.subs.pop().getInt()
        val array = frame.subs.pop().getArray()

        for (i in 0 until count) {
            array[index + i] = frame.subs.pop()
        }
    }

}