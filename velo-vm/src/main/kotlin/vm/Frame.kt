package vm

import core.Op

/**
 * A live call frame: the instructions of one compiled frame plus this
 * activation's program counter, operand stack and variable chain.
 */
data class Frame(
    var pc: Int,
    val subs: Stack<Record>,
    val vars: Vars,
    val ops: List<Op>,
)
