package vm.operations

import vm.Activation
import vm.Heap
import vm.Record
import vm.SimpleOperation
import vm.Stack

class Index : SimpleOperation {

    override fun exec(dataStack: Stack<Record>, callStack: Stack<Activation>, heap: Heap) {
        val index = dataStack.pop().getInt()
        val array = dataStack.pop().getArray()

        val rec = array[index]

        dataStack.push(rec)
    }

}