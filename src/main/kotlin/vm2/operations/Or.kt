package vm2.operations

import vm2.*
import vm2.records.ValueRecord
import java.util.*

class Or: SimpleOperation {

    override fun exec(dataStack: Deque<Record>, callStack: Deque<Activation>, heap: Heap) {
        val val1 = dataStack.pop().getBool()
        val val2 = dataStack.pop().getBool()

        val result = ValueRecord(val1.or(val2))

        dataStack.push(result)
    }

}