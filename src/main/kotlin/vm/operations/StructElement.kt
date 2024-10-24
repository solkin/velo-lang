package vm.operations

import vm.Activation
import vm.Heap
import vm.Record
import vm.SimpleOperation
import vm.Stack

class StructElement : SimpleOperation {

    override fun exec(dataStack: Stack<Record>, callStack: Stack<Activation>, heap: Heap) {
        val index = dataStack.pop().getInt()
        val struct = dataStack.pop().getStruct()

        dataStack.push(struct[index])
    }

}
