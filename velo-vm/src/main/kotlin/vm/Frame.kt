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
    // Mutable so a loop body can push/pop a fresh per-iteration scope
    // (Op.ScopeEnter/Op.ScopeLeave) without a call frame — the environment
    // changes, control does not.
    var vars: Vars,
    val ops: List<Op>,
    val num: Int = -1,
)
