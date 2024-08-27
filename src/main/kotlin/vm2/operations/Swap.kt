package vm2.operations

import vm2.*
import java.util.*

class Swap: SimpleOperation {

    override fun exec(dataStack: Queue<Record>, callStack: Queue<Activation>, heap: Heap) {
        val rec1 = dataStack.remove()
        val rec2 = dataStack.remove()
        dataStack.add(rec1)
        dataStack.add(rec2)
    }

}