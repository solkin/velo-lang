package vm2.operations

import vm2.*
import java.util.*

class Get(
    val index: Int,
): SimpleOperation {

    override fun exec(dataStack: Queue<Record>, callStack: Queue<Activation>, heap: Heap) {
        val scope = heap.current()
        val rec = scope.get(index)
        dataStack.add(rec)
    }

}