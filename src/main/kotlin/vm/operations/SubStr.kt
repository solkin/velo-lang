package vm.operations

import vm.Frame
import vm.Heap
import vm.Record
import vm.SimpleOperation
import vm.Stack
import vm.records.ValueRecord

class SubStr : SimpleOperation {

    override fun exec(subs: Stack<Record>, heap: Heap) {
        val start = subs.pop().getInt()
        val end = subs.pop().getInt()
        val str = subs.pop().getString()

        val rec = ValueRecord(str.substring(start, end))

        subs.push(rec)
    }

}