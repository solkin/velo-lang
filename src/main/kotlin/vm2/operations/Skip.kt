package vm2.operations

import vm2.*
import java.util.*

class Skip(
    private val count: Int
) : Operation {

    override fun exec(pc: Int, dataStack: Deque<Record>, callStack: Deque<Activation>, heap: Heap): Int {
        return pc + count + 1
    }

}