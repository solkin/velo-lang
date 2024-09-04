package vm2.operations

import vm2.*
import vm2.Stack

class Ret: Operation {

    override fun exec(pc: Int, dataStack: Stack<Record>, callStack: Stack<Activation>, heap: Heap): Int {
        val activation = callStack.pop()
        heap.free()
        return activation.addr
    }

}