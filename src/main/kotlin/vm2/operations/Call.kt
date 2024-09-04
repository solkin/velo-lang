package vm2.operations

import vm2.*
import vm2.Stack

class Call(
    private val args: List<Int>,
) : Operation {

    override fun exec(pc: Int, dataStack: Stack<Record>, callStack: Stack<Activation>, heap: Heap): Int {
        val addr = dataStack.pop().getInt()

        val scope = heap.extend()
        for (arg in args) {
            val rec = dataStack.pop()
            scope.def(arg, rec)
        }

        val activation = Activation(
            addr = pc + 1,
        )
        callStack.push(activation)
        return addr
    }

}