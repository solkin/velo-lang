package vm.operations

import vm.Frame
import vm.SimpleOperation
import vm.records.LinkRecord

class ArrStore : SimpleOperation {

    override fun exec(frame: Frame) {
        val count = frame.subs.pop().getInt()
        val index = frame.subs.pop().getInt()
        val array = frame.subs.pop().getArray()

        var i = index + count - 1
        repeat(count) {
            array[i--] = frame.subs.pop()
        }

        frame.subs.push(value = LinkRecord.create(array))
    }

}