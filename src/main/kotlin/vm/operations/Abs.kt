package vm.operations

import vm.Heap
import vm.Record
import vm.SimpleOperation
import vm.Stack
import vm.records.ValueRecord
import kotlin.math.abs

class Abs: SimpleOperation {

    override fun exec(subs: Stack<Record>, heap: Heap) {
        val rec = subs.pop()

        val result = ValueRecord(abs(rec.getInt()))

        subs.push(result)
    }

}