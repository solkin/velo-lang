package vm2.operations

import vm2.*
import java.util.*

class Drop: SimpleOperation {

    override fun exec(dataStack: Queue<Record>, callStack: Queue<Activation>, heap: Heap) {
        dataStack.remove()
    }

}