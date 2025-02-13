package vm.operations

import vm.Frame
import vm.SimpleOperation
import vm.records.LinkRecord

class Pair: SimpleOperation {

    override fun exec(frame: Frame) {
        val rec1 = frame.subs.pop()
        val rec2 = frame.subs.pop()

        val result = LinkRecord.create(Pair(rec2, rec1))

        frame.subs.push(result)
    }

}
