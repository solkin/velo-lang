package vm2.operations

import vm2.*
import java.util.*

class If(
    private val addr: Int
) : Operation {

    override fun exec(pc: Int, dataStack: Deque<Record>, callStack: Deque<Activation>, heap: Heap): Int {
        val flag = dataStack.pop().getBool()
        return if (flag) {
            addr
        } else {
            pc + 1
        }
    }

}