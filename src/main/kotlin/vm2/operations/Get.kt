package vm2.operations

import vm2.*
import java.util.*

class Get(
    val index: Int,
): SimpleOperation {

    override fun exec(dataStack: Deque<Record>, callStack: Deque<Activation>, heap: Heap) {
        val scope = heap.current()
        val rec = scope.get(index)
        dataStack.push(rec)
    }

}