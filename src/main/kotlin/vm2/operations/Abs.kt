package vm2.operations

import vm2.*
import vm2.records.ValueRecord
import java.util.*
import kotlin.math.abs

class Abs: SimpleOperation {

    override fun exec(dataStack: Queue<Record>, callStack: Queue<Activation>, heap: Heap) {
        val rec = dataStack.remove()

        val result = ValueRecord(abs(rec.getInt()))

        dataStack.add(result)
    }

}