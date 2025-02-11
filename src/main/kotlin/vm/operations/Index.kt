package vm.operations

import vm.Frame
import vm.SimpleOperation

class Index : SimpleOperation {

    override fun exec(frame: Frame) {
        val index = frame.subs.pop().getInt()
        val array = frame.subs.pop().getArray()

        val rec = array[index]

        frame.subs.push(rec)
    }

}