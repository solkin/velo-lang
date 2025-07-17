package vm.operations

import vm.Frame
import vm.SimpleOperation

class TupleEntryGet(val index: Int) : SimpleOperation {

    override fun exec(frame: Frame) {
        val tuple = frame.subs.pop().getArray()

        frame.subs.push(tuple[index])
    }

}
