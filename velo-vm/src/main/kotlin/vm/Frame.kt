package vm

import core.Op

/**
 * A live call frame: the instructions of one compiled frame plus this
 * activation's program counter, operand stack and variable chain.
 *
 * [num] is the bytecode frame number this activation was loaded from
 * (-1 for synthetic frames such as the actor sentinel). A class-instance
 * frame keeps the number of the class it was built from, which lets the
 * runtime recognise `data class` instances and marshal them by value.
 */
data class Frame(
    var pc: Int,
    val subs: Stack<Record>,
    val vars: Vars,
    val ops: List<Op>,
    val num: Int = -1,
)
