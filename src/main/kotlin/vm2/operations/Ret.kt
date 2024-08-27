package vm2.operations

import vm2.*
import java.util.*

class Ret: Operation {

    override fun exec(pc: Int, dataStack: Queue<Record>, callStack: Queue<Activation>, heap: Heap): Int {
        val activation = callStack.remove()
        activation.ret?.let { ret ->
            dataStack.add(ret)
        }
        return activation.addr
    }

}