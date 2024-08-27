package vm2.operations

import vm2.*
import vm2.records.ValueRecord
import java.util.*

class Negative: SimpleOperation {

    override fun exec(dataStack: Queue<Record>, callStack: Queue<Activation>, heap: Heap) {
        val rec = dataStack.remove().getInt()

        val result = ValueRecord(-rec)

        dataStack.add(result)
    }

}