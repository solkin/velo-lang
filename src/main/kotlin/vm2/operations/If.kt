package vm2.operations

import vm2.*
import java.util.*

class If(
    private val addr: Int
) : Operation {

    override fun exec(pc: Int, dataStack: Queue<Record>, callStack: Queue<Activation>, heap: Heap): Int {
        val flag = dataStack.remove().getBool()
        return if (flag) {
            addr
        } else {
            pc + 1
        }
    }

}