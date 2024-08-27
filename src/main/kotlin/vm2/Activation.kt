package vm2

data class Activation(
    val addr: Int,
    val args: List<Record>,
    val ret: Record?,
)
