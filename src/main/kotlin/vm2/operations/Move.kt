package vm2.operations

import vm2.*
import vm2.Stack

class Move(
    private val count: Int
) : Operation {

    override fun exec(pc: Int, dataStack: Stack<Record>, callStack: Stack<Activation>, heap: Heap): Int {
        return pc + count + 1
    }

}