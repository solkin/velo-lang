package vm2.operations

import vm2.*
import vm2.Stack

class Index : SimpleOperation {

    override fun exec(dataStack: Stack<Record>, callStack: Stack<Activation>, heap: Heap) {
        val index = dataStack.pop().getInt()
        val slice = dataStack.pop().getSlice()

        val rec = slice[index]

        dataStack.push(rec)
    }

}