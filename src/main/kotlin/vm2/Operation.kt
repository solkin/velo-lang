package vm2

import java.util.*

interface Operation {

    fun exec(pc: Int, dataStack: Queue<Record>, callStack: Queue<Activation>, heap: Heap): Int

}
