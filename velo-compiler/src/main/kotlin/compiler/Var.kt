package compiler

import compiler.nodes.Type

data class Var(
    val index: Int,
    val type: Type,
    /** Reassignment is rejected at compile time (e.g. `data class` fields). */
    val immutable: Boolean = false,
)