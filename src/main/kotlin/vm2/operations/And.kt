package vm2.operations

import vm2.*
import vm2.records.ValueRecord
import java.util.*

class And: SimpleOperation {

    override fun exec(dataStack: Queue<Record>, callStack: Queue<Activation>, heap: Heap) {
        val val1 = dataStack.remove().getBool()
        val val2 = dataStack.remove().getBool()

        val result = ValueRecord(val1.and(val2))

        dataStack.add(result)
    }

}