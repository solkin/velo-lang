package vm.operations

import vm.Frame
import vm.Heap
import vm.Record
import vm.SimpleOperation
import vm.Stack
import vm.records.ValueRecord
import java.util.Arrays

class SubArr : SimpleOperation {

    override fun exec(subs: Stack<Record>, heap: Heap) {
        val start = subs.pop().getInt()
        val end = subs.pop().getInt()
        val array = subs.pop().getArray()

        val rec = ValueRecord(Arrays.copyOfRange(array, start, end))

        subs.push(rec)
    }

}