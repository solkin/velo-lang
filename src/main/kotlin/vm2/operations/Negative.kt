package vm2.operations

import vm2.*
import vm2.records.ValueRecord
import java.util.*

class Negative: SimpleOperation {

    override fun exec(dataStack: Deque<Record>, callStack: Deque<Activation>, heap: Heap) {
        val rec = dataStack.pop().getInt()

        val result = ValueRecord(-rec)

        dataStack.push(result)
    }

}