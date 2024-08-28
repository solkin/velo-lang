package vm2.operations

import vm2.*
import vm2.records.ValueRecord
import java.util.*

class Push(
    val value: Any
): SimpleOperation {

    override fun exec(dataStack: Deque<Record>, callStack: Deque<Activation>, heap: Heap) {
        val rec = ValueRecord(value)
        dataStack.push(rec)
    }

}