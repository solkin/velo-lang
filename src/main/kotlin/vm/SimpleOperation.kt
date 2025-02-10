package vm

interface SimpleOperation : Operation {

    override fun exec(pc: Int, stack: Stack<Frame>, heap: Heap): Int {
        exec(stack.peek().subs, heap)
        return pc + 1
    }

    fun exec(subs: Stack<Record>, heap: Heap)

}
