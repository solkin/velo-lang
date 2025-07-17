package vm.operations

import vm.Frame
import vm.SimpleOperation

class TupleEntrySet(val index: Int) : SimpleOperation {

    override fun exec(frame: Frame) {
        val tuple = frame.subs.pop().getArray()
        val record = frame.subs.pop()

        tuple[index] = record
    }

}
