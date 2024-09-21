package vm.operations

import vm.Activation
import vm.Heap
import vm.Record
import vm.SimpleOperation
import vm.Stack

class Index : SimpleOperation {

    override fun exec(dataStack: Stack<Record>, callStack: Stack<Activation>, heap: Heap) {
        val index = dataStack.pop().getInt()
        val slice = dataStack.pop().getSlice()

        val rec = slice[index]

        dataStack.push(rec)
    }

}