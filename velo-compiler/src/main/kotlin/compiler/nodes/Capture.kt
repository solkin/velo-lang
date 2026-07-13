package compiler.nodes

import core.Op

/**
 * The variable slot this op reads or writes, or `null` if it does not address a
 * slot by index. The single source of truth for slot addressing, shared by
 * every capture analysis: a function is a closure when it touches a slot below
 * its own frame base ([FuncNode]), and a loop needs a per-iteration scope when a
 * body closure touches a body-local slot ([capturesLoopScope]).
 *
 * `Op.ScopeEnter.base` is deliberately excluded: it only reserves a slot range
 * for a nested per-iteration scope, it does not read or write a variable's
 * value — a genuine capture always surfaces as one of the ops below. Add any new
 * slot-addressing op here and both analyses stay correct.
 */
fun Op.slotIndex(): Int? = when (this) {
    is Op.Load -> index
    is Op.Store -> index
    is Op.PtrRef -> varIndex
    else -> null
}
