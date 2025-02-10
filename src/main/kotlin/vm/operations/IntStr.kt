package vm.operations

import vm.Frame
import vm.Heap
import vm.Record
import vm.SimpleOperation
import vm.Stack
import vm.records.ValueRecord

class IntStr : SimpleOperation {

    override fun exec(subs: Stack<Record>, heap: Heap) {
        val v = subs.pop().getInt()

        val rec = ValueRecord(v.toString())

        subs.push(rec)
    }

}