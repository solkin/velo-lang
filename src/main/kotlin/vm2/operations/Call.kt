package vm2.operations

import vm2.*
import vm2.records.PointerRecord
import java.util.*

class Call(
    private val argsCount: Int,
    private val hasRet: Boolean,
) : Operation {

    override fun exec(pc: Int, dataStack: Queue<Record>, callStack: Queue<Activation>, heap: Heap): Int {
        val addr = dataStack.remove().getInt()
        val args = ArrayList<Record>()
        for (i in 0 until argsCount) {
            args.add(dataStack.remove())
        }

        val scope = heap.extend()

        val ret = if (hasRet) {
            scope.set(0, PointerRecord(scope, 0))
        } else {
            null
        }

        val activation = Activation(
            addr = pc + 1,
            args = args,
            ret = ret,
        )
        callStack.add(activation)
        return addr
    }

}