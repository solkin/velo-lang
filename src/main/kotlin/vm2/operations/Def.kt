package vm2.operations

import vm2.*
import vm2.records.ValueRecord
import java.util.*

class Def(
    val index: Int,
): SimpleOperation {

    override fun exec(dataStack: Queue<Record>, callStack: Queue<Activation>, heap: Heap) {
        val value = dataStack.remove().get()
        val scope = heap.current()
        scope.def(index, ValueRecord(value))
    }

}