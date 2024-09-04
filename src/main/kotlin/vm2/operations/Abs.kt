package vm2.operations

import vm2.*
import vm2.records.ValueRecord
import kotlin.math.abs

class Abs: SimpleOperation {

    override fun exec(dataStack: Stack<Record>, callStack: Stack<Activation>, heap: Heap) {
        val rec = dataStack.pop()

        val result = ValueRecord(abs(rec.getInt()))

        dataStack.push(result)
    }

}