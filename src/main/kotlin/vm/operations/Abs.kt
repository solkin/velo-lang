package vm.operations

import vm.Activation
import vm.Heap
import vm.Record
import vm.SimpleOperation
import vm.Stack
import vm.records.ValueRecord
import kotlin.math.abs

class Abs: SimpleOperation {

    override fun exec(dataStack: Stack<Record>, callStack: Stack<Activation>, heap: Heap) {
        val rec = dataStack.pop()

        val result = ValueRecord(abs(rec.getInt()))

        dataStack.push(result)
    }

}