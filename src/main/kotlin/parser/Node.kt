package parser

open class Node(val type: NodeType)

data class NumNode(
    val value: Double,
) : Node(NodeType.NUMBER)

data class StrNode(
    val value: String,
) : Node(NodeType.STRING)

data class BoolNode(
    val value: Boolean,
) : Node(NodeType.BOOLEAN)

data class VarNode(
    val name: String,
) : Node(NodeType.VARIABLE)

data class LambdaNode(
    val vars: List<String>,
    val body: Node,
) : Node(NodeType.LAMBDA)

data class CallNode(
    val name: String,
    val func: Node,
    val args: List<Node>,
) : Node(NodeType.CALL)

data class IfNode(
    val condNode: Node,
    val thenNode: Node,
    val elseNode: Node?,
) : Node(NodeType.IF)

data class AssignNode(
    val left: Node,
    val right: Node,
) : Node(NodeType.ASSIGN)

data class BinaryNode(
    val operator: Char,
    val left: Node,
    val right: Node,
) : Node(NodeType.BINARY)

data class ProgramNode(
    val prog: List<Node>,
) : Node(NodeType.PROGRAM)

data class LetNode(
    val name: String,
    val vars: List<String>,
    val body: Node,
) : Node(NodeType.LET)

enum class NodeType(val type: String) {
    NUMBER("num"),
    STRING("str"),
    BOOLEAN("bool"),
    VARIABLE("var"),
    LAMBDA("lambda"),
    CALL("call"),
    IF("if"),
    ASSIGN("assign"),
    BINARY("binary"),
    PROGRAM("prog"),
    LET("let"),
}
