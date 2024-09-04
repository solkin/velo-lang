package vm2.operations

import vm2.*
import vm2.Stack

class If(
    private val addr: Int
) : Operation {

    override fun exec(pc: Int, dataStack: Stack<Record>, callStack: Stack<Activation>, heap: Heap): Int {
        val flag = dataStack.pop().getBool()
        return if (flag) {
            addr
        } else {
            pc + 1
        }
    }

}