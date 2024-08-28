package vm2

import java.util.*

interface Operation {

    fun exec(pc: Int, dataStack: Deque<Record>, callStack: Deque<Activation>, heap: Heap): Int

}
