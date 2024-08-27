package vm2.operations

import vm2.*
import java.util.*

class Goto(
    private val addr: Int
) : Operation {

    override fun exec(pc: Int, dataStack: Queue<Record>, callStack: Queue<Activation>, heap: Heap): Int {
        return addr
    }

}