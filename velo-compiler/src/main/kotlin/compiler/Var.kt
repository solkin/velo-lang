package compiler

import compiler.nodes.Type

data class Var(
    val index: Int,
    val type: Type,
    /** Reassignment is rejected at compile time (e.g. `data class` fields). */
    val immutable: Boolean = false,
) {
    /**
     * The body frame of a **direct function/ext declaration** — the identity of
     * the concrete function this name denotes. Set by [compiler.nodes.FuncNode]
     * on the declaring binding; `null` on every plain variable, including one
     * that merely holds a function value. A direct call of such a name is
     * addressed by frame number (runs from any thread); everything else is called
     * through its variable, which carries the real runtime value. Identity lives
     * on the symbol, not on the type (the type is just the `(args) ret`
     * signature), so it never leaks through inference, an `if` expression, or a
     * reassignment — those produce a fresh [Var] with `funcFrameNum == null`.
     */
    var funcFrameNum: Int? = null
}