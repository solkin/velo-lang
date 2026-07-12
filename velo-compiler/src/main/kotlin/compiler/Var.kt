package compiler

import compiler.nodes.Type

data class Var(
    val index: Int,
    val type: Type,
    /** Reassignment is rejected at compile time (e.g. `data class` fields). */
    val immutable: Boolean = false,
    /**
     * True only for a direct function/ext declaration — the one binding whose
     * runtime value is guaranteed to be exactly this function. Such a call can be
     * addressed by frame number (see [compiler.nodes.FuncType.frameNum]). A plain
     * variable that merely holds a function value is not frame-addressable: its
     * static type may carry a `frameNum` (copied by inference or an `if`
     * expression) while its runtime value is a different function, so it must be
     * called through its variable. Cleared on reassignment (see [CompilerFrame.retype]).
     */
    val frameAddressable: Boolean = false,
)