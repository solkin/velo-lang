package vm2.operations

import vm2.Activation
import vm2.Heap
import vm2.Operation
import vm2.Record
import vm2.Stack

class Call : Operation {

    override fun exec(pc: Int, dataStack: Stack<Record>, callStack: Stack<Activation>, heap: Heap): Int {
        val addr = dataStack.pop().getInt()
        heap.extend()
        val activation = Activation(addr = pc + 1)
        callStack.push(activation)
        return addr
    }

}