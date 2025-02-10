package vm.operations

import vm.Frame
import vm.Heap
import vm.Record
import vm.SimpleOperation
import vm.Stack
import vm.records.ValueRecord

class Negative: SimpleOperation {

    override fun exec(subs: Stack<Record>, heap: Heap) {
        val rec = subs.pop().getInt()

        val result = ValueRecord(-rec)

        subs.push(result)
    }

}