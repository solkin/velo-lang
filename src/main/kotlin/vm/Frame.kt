package vm

data class Frame(
    val addr: Int,
    val subs: Stack<Record>,
)
