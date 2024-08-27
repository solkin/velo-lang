package vm2.operations

import vm2.*
import vm2.records.ValueRecord
import java.util.*

class Plus: SimpleOperation {

    override fun exec(dataStack: Queue<Record>, callStack: Queue<Activation>, heap: Heap) {
        val rec1 = dataStack.remove()
        val rec2 = dataStack.remove()

        val result = ValueRecord(rec1.getInt() + rec2.getInt())

        dataStack.add(result)
    }

}