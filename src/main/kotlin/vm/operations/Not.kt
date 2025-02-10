package vm.operations

import vm.Frame
import vm.Heap
import vm.Record
import vm.SimpleOperation
import vm.Stack
import vm.records.ValueRecord

class Not: SimpleOperation {

    override fun exec(subs: Stack<Record>, heap: Heap) {
        val rec = subs.pop()

        val result = ValueRecord(rec.getBool().not())

        subs.push(result)
    }

}