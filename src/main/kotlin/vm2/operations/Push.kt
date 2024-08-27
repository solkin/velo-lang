package vm2.operations

import vm2.*
import vm2.records.ValueRecord
import java.util.*

class Push(
    val value: Any
): SimpleOperation {

    override fun exec(dataStack: Queue<Record>, callStack: Queue<Activation>, heap: Heap) {
        val rec = ValueRecord(value)
        dataStack.add(rec)
    }

}