package vm2.operations

import vm2.*
import java.util.*

class Print : SimpleOperation {

    override fun exec(dataStack: Queue<Record>, callStack: Queue<Activation>, heap: Heap) {
        val value = dataStack.remove().get()
        print(value.toString())
    }

}