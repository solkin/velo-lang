package compiler.nodes

import compiler.Context
import vm.operations.Push
import vm.operations.Sub

data class UnaryNode(
    val operator: String,
    val operand: Node,
) : Node() {
    override fun compile(ctx: Context): Type {
        return when (operator) {
            "-" -> {
                // Push zero first, then operand, then subtract: 0 - operand = -operand
                // We need to compile operand first to get its type, then emit the right zero
                val operandType = operand.compile(ctx)
                when (operandType) {
                    is ByteType -> ctx.add(Push(0.toByte()))
                    is FloatType -> ctx.add(Push(0f))
                    else -> ctx.add(Push(0))
                }
                // Swap so zero is below operand: stack becomes [0, operand]
                ctx.add(vm.operations.Swap())
                // Sub pops operand (rec1), pops 0 (rec2), computes 0 - operand
                ctx.add(Sub())
                operandType
            }
            else -> throw IllegalArgumentException("Unknown unary operator: $operator")
        }
    }
}

