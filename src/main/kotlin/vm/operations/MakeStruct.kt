package vm.operations

import vm.Heap
import vm.Record
import vm.SimpleOperation
import vm.Stack
import vm.records.ValueRecord

class MakeStruct : SimpleOperation {

    override fun exec(subs: Stack<Record>, heap: Heap) {
        val count = subs.pop().getInt()

        val elements = ArrayList<Record>()
        for (i in 0 until count) {
            val rec = subs.pop()
            elements.add(rec)
        }
        val result = ValueRecord(elements)

        subs.push(result)
    }

}
