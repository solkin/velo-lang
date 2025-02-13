package vm.operations

import vm.Frame
import vm.SimpleOperation
import vm.records.LinkRecord

class ArrOf : SimpleOperation {

    override fun exec(frame: Frame) {
        val size = frame.subs.pop().getInt()
        val array = Array(size) { _ ->
            frame.subs.pop()
        }.apply { reverse() }
        val rec = LinkRecord.create(array)
        frame.subs.push(rec)
    }

}