package compiler.nodes

data class StructNode(
    val nodes: List<DefNode>,
) : Node()
