package vm2.operations

import vm2.*
import vm2.records.ValueRecord
import java.util.*

class Not: SimpleOperation {

    override fun exec(dataStack: Queue<Record>, callStack: Queue<Activation>, heap: Heap) {
        val rec = dataStack.remove()

        val result = ValueRecord(rec.getBool().not())

        dataStack.add(result)
    }

}