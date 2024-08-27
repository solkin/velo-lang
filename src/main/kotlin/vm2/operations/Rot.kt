package vm2.operations

import vm2.*
import java.util.*

class Rot: SimpleOperation {

    override fun exec(dataStack: Queue<Record>, callStack: Queue<Activation>, heap: Heap) {
        val rec1 = dataStack.remove()
        val rec2 = dataStack.remove()
        val rec3 = dataStack.remove()
        dataStack.add(rec2)
        dataStack.add(rec1)
        dataStack.add(rec3)
    }

}