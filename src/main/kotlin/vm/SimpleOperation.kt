package vm

interface SimpleOperation : Operation {

    override fun exec(pc: Int, stack: Stack<Frame>, resources: Resources): Int {
        exec(stack.peek())
        return pc + 1
    }

    fun exec(frame: Frame)

}
