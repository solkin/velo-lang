package vm

data class Frame(
    var pc: Int,
    val subs: Stack<Record>,
    val vars: Vars,
    val ops: List<Operation>,
)
