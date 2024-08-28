package vm2.operations

import vm2.*
import java.util.*

class Print : SimpleOperation {

    override fun exec(dataStack: Deque<Record>, callStack: Deque<Activation>, heap: Heap) {
        val value = dataStack.pop().get()
        print(value.toString())
    }

}