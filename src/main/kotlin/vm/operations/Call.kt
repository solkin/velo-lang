package vm.operations

import vm.Activation
import vm.Heap
import vm.Operation
import vm.Record
import vm.Stack

class Call : Operation {

    override fun exec(pc: Int, dataStack: Stack<Record>, callStack: Stack<Activation>, heap: Heap): Int {
        val addr = dataStack.pop().getInt()
        heap.extend()
        val activation = Activation(addr = pc + 1)
        callStack.push(activation)
        return addr
    }

}