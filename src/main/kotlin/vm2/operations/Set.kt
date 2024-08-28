package vm2.operations

import vm2.*
import vm2.records.ValueRecord
import java.util.*

class Set(
    val index: Int,
): SimpleOperation {

    override fun exec(dataStack: Deque<Record>, callStack: Deque<Activation>, heap: Heap) {
        val value = dataStack.pop().get()
        val scope = heap.current()
        scope.set(index, ValueRecord(value))
    }

}