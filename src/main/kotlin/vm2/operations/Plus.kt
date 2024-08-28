package vm2.operations

import vm2.*
import vm2.records.ValueRecord
import java.util.*

class Plus: SimpleOperation {

    override fun exec(dataStack: Deque<Record>, callStack: Deque<Activation>, heap: Heap) {
        val rec1 = dataStack.pop()
        val rec2 = dataStack.pop()

        val result = ValueRecord(rec1.getInt() + rec2.getInt())

        dataStack.push(result)
    }

}