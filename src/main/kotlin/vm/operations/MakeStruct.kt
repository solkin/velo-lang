package vm.operations

import vm.Frame
import vm.Record
import vm.SimpleOperation
import vm.records.ValueRecord

class MakeStruct : SimpleOperation {

    override fun exec(frame: Frame) {
        val count = frame.subs.pop().getInt()

        val elements = ArrayList<Record>()
        for (i in 0 until count) {
            val rec = frame.subs.pop()
            elements.add(rec)
        }
        val result = ValueRecord(elements)

        frame.subs.push(result)
    }

}
