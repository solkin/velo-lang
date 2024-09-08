package vm2.operations

import vm2.*

class Free : SimpleOperation {

    override fun exec(dataStack: Stack<Record>, callStack: Stack<Activation>, heap: Heap) {
        heap.free()
    }

}
