package compiler.nodes

import core.Op

import compiler.Context

data class UnaryNode(
    val operator: String,
    val operand: Node,
) : Node() {
    override fun compile(ctx: Context): Type {
        return when (operator) {
            "-" -> {
                val operandType = operand.compile(ctx)
                if (operandType is ClassType) {
                    val prop = ClassElementProp("op@neg")
                    try {
                        return prop.compile(operandType, args = emptyList(), ctx)
                    } catch (e: IllegalArgumentException) {
                        throw IllegalArgumentException("Unary operator '-' is not defined for class '${operandType.name}'")
                    }
                }
                when (operandType) {
                    is ByteType -> ctx.add(Op.Push(0.toByte()))
                    is FloatType -> ctx.add(Op.Push(0f))
                    else -> ctx.add(Op.Push(0))
                }
                ctx.add(Op.Swap)
                ctx.add(Op.Sub)
                operandType
            }
            else -> throw IllegalArgumentException("Unknown unary operator: $operator")
        }
    }
}

