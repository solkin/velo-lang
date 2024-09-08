package vm2.operations

import vm2.*
import vm2.Stack

class If(
    private val elseSkip: Int
) : Operation {

    override fun exec(pc: Int, dataStack: Stack<Record>, callStack: Stack<Activation>, heap: Heap): Int {
        val flag = dataStack.pop().getBool()
        return pc + 1 + if (flag) 0 else elseSkip
    }

}