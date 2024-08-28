package vm2.operations

import vm2.*
import java.util.*

class Ret: Operation {

    override fun exec(pc: Int, dataStack: Deque<Record>, callStack: Deque<Activation>, heap: Heap): Int {
        val activation = callStack.pop()
        heap.free()
        return activation.addr
    }

}