package vm2.operations

import vm2.*
import vm2.records.ValueRecord
import java.util.*
import kotlin.math.abs

class Abs: SimpleOperation {

    override fun exec(dataStack: Deque<Record>, callStack: Deque<Activation>, heap: Heap) {
        val rec = dataStack.pop()

        val result = ValueRecord(abs(rec.getInt()))

        dataStack.push(result)
    }

}