package vm

interface SimpleOperation : Operation {

    override fun exec(pc: Int, stack: Stack<Frame>, frameLoader: FrameLoader): Int {
        exec(stack.peek())
        return pc + 1
    }

    fun exec(frame: Frame)

}
