package vm2.operations

import vm2.*
import java.util.*

class Pick: SimpleOperation {

    override fun exec(dataStack: Queue<Record>, callStack: Queue<Activation>, heap: Heap) {
        throw OperationNotSupportedException()
    }

}