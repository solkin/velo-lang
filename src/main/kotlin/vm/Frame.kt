package vm

data class Frame(
    val pc: Int,
    val subs: Stack<Record>,
    val vars: Vars,
)
