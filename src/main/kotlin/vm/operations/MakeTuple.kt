package vm.operations

import vm.Frame
import vm.SimpleOperation
import vm.records.LinkRecord

class MakeTuple(val size: Int) : SimpleOperation {

    override fun exec(frame: Frame) {
        val recs = Array(size) { frame.subs.pop() }
        recs.reverse()

        val result = LinkRecord.create(recs)

        frame.subs.push(result)
    }

}
