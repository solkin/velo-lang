package vm

interface Operation {
    fun exec(pc: Int, stack: Stack<Frame>, resources: Resources): Int
}
