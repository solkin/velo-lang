package vm2.operations

import vm2.*
import vm2.records.ValueRecord
import java.util.*

class Not: SimpleOperation {

    override fun exec(dataStack: Deque<Record>, callStack: Deque<Activation>, heap: Heap) {
        val rec = dataStack.pop()

        val result = ValueRecord(rec.getBool().not())

        dataStack.push(result)
    }

}