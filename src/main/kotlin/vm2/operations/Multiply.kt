package vm2.operations

import vm2.*
import vm2.records.ValueRecord
import java.util.*

class Multiply: SimpleOperation {

    override fun exec(dataStack: Deque<Record>, callStack: Deque<Activation>, heap: Heap) {
        val rec1 = dataStack.pop().getInt()
        val rec2 = dataStack.pop().getInt()

        val result = ValueRecord(rec1 * rec2)

        dataStack.push(result)
    }

}