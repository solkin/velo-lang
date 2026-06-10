package core

/**
 * One compiled frame: its number (referenced by `Op.Frame`/`Op.Call`),
 * its instructions, and the variable indices it declares.
 */
data class SerializedFrame(
    val num: Int,
    val ops: List<Op>,
    val vars: List<Int>,
)
