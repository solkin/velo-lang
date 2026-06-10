package compiler

import compiler.nodes.Type

data class Var(
    val index: Int,
    val type: Type,
)