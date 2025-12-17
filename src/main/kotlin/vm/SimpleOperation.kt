package vm

interface SimpleOperation : Operation {

    override fun exec(pc: Int, ctx: VMContext): Int {
        exec(ctx.currentFrame(), ctx)
        return pc + 1
    }

    fun exec(frame: Frame, ctx: VMContext)

}
