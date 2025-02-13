package vm.operations

import vm.Frame
import vm.SimpleOperation
import vm.records.ValueRecord

class ArrSet : SimpleOperation {

    override fun exec(frame: Frame) {
        val index = frame.subs.pop().getInt()
        val value = frame.subs.pop()
        val array = frame.subs.pop().getArray()

        array[index] = value

        frame.subs.push(ValueRecord(array))
    }

}