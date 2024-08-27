package vm2.operations

import vm2.*
import java.util.*

class Dup: SimpleOperation {

    override fun exec(dataStack: Queue<Record>, callStack: Queue<Activation>, heap: Heap) {
        val rec = dataStack.peek()
        dataStack.add(rec)
    }

}