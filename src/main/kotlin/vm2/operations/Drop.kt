package vm2.operations

import vm2.*
import java.util.*

class Drop: SimpleOperation {

    override fun exec(dataStack: Deque<Record>, callStack: Deque<Activation>, heap: Heap) {
        dataStack.pop()
    }

}