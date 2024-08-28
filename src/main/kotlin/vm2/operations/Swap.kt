package vm2.operations

import vm2.*
import java.util.*

class Swap: SimpleOperation {

    override fun exec(dataStack: Deque<Record>, callStack: Deque<Activation>, heap: Heap) {
        val rec1 = dataStack.pop()
        val rec2 = dataStack.pop()
        dataStack.push(rec1)
        dataStack.push(rec2)
    }

}