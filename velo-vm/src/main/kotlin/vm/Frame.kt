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
) {
    /**
     * Active `try` error handlers, innermost last (VEL-9). Null until the first
     * [Op.TryEnter] in this frame, so frames without a `try` pay nothing. Kept on
     * the frame — not in a context-wide stack — so it rides along for free when a
     * fiber's stack is detached/restored across an `await`. Declared outside the
     * constructor so it stays out of the data-class `equals`/`copy`/identity.
     */
    var handlers: ArrayDeque<Handler>? = null
}

/**
 * One active `try`: where to resume on a caught error ([catchPc]), plus the
 * marks needed to restore this frame to its pre-`try` shape while unwinding —
 * the variable scope ([savedVars], undoing any open [Op.ScopeEnter]) and the
 * operand-stack depth ([subsDepth], dropping half-evaluated operands).
 */
class Handler(
    val catchPc: Int,
    val savedVars: Vars,
    val subsDepth: Int,
)
