package vm2.operations

import vm2.*
import java.util.*

class Dup: SimpleOperation {

    override fun exec(dataStack: Deque<Record>, callStack: Deque<Activation>, heap: Heap) {
        val rec = dataStack.peek()
        dataStack.push(rec)
    }

}