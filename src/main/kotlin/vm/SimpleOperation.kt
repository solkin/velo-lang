package vm

interface SimpleOperation : Operation {

    override fun exec(pc: Int, dataStack: Stack<Record>, callStack: Stack<Activation>, heap: Heap): Int {
        exec(dataStack, callStack, heap)
        return pc + 1
    }

    fun exec(dataStack: Stack<Record>, callStack: Stack<Activation>, heap: Heap)

}
