package vm2.operations

import vm2.*
import vm2.records.ValueRecord
import java.util.*

class Equals: SimpleOperation {

    override fun exec(dataStack: Queue<Record>, callStack: Queue<Activation>, heap: Heap) {
        val val1 = dataStack.remove().getInt()
        val val2 = dataStack.remove().getInt()

        val result = ValueRecord(val1 == val2)

        dataStack.add(result)
    }

}