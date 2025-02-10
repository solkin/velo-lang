package vm.operations

import vm.Frame
import vm.Heap
import vm.Record
import vm.SimpleOperation
import vm.Stack
import vm.records.ValueRecord
import kotlin.Pair

class Pair: SimpleOperation {

    override fun exec(subs: Stack<Record>, heap: Heap) {
        val rec1 = subs.pop()
        val rec2 = subs.pop()

        val result = ValueRecord(Pair(rec2, rec1))

        subs.push(result)
    }

}
