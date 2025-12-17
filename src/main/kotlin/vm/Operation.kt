package vm

interface Operation {
    fun exec(pc: Int, ctx: VMContext): Int
}
