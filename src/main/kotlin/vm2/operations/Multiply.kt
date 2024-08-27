package vm2.operations

import vm2.*
import vm2.records.ValueRecord
import java.util.*

class Multiply: SimpleOperation {

    override fun exec(dataStack: Queue<Record>, callStack: Queue<Activation>, heap: Heap) {
        val rec1 = dataStack.remove().getInt()
        val rec2 = dataStack.remove().getInt()

        val result = ValueRecord(rec1 * rec2)

        dataStack.add(result)
    }

}