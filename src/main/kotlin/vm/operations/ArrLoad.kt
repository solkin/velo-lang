package vm.operations

import vm.VMContext

import vm.Frame
import vm.SimpleOperation

class ArrLoad : SimpleOperation {

    override fun exec(frame: Frame, ctx: VMContext) {
        val count = frame.subs.pop().getInt()
        val index = frame.subs.pop().getInt()
        val array = frame.subs.pop().getArray()

        for (i in 0 until count) {
            frame.subs.push(value = array[index + i])
        }
    }

}