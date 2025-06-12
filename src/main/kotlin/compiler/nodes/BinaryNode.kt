package compiler.nodes

import compiler.Context
import vm.operations.And
import vm.operations.Divide
import vm.operations.Equals
import vm.operations.Less
import vm.operations.LessEquals
import vm.operations.Minus
import vm.operations.More
import vm.operations.MoreEquals
import vm.operations.Multiply
import vm.operations.Not
import vm.operations.Or
import vm.operations.Plus
import vm.operations.Rem

data class BinaryNode(
    val operator: String,
    val left: Node,
    val right: Node,
) : Node() {
    override fun compile(ctx: Context): Type {
        val leftType = left.compile(ctx)
        val rightType = right.compile(ctx)
        if (!leftType.sameAs(rightType)) {
            throw IllegalArgumentException("Binary operation with different types $leftType [$operator] $rightType")
        }
        return when (operator) {
            "+" -> {
                ctx.add(Plus())
                leftType
            }

            "-" -> {
                ctx.add(Minus())
                leftType
            }

            "*" -> {
                ctx.add(Multiply())
                leftType
            }

            "/" -> {
                ctx.add(Divide())
                leftType
            }

            "%" -> {
                ctx.add(Rem())
                leftType
            }

            "<" -> {
                ctx.add(Less())
                BoolType
            }

            ">" -> {
                ctx.add(More())
                BoolType
            }

            "==" -> {
                ctx.add(Equals())
                BoolType
            }

            "<=" -> {
                ctx.add(LessEquals())
                BoolType
            }

            ">=" -> {
                ctx.add(MoreEquals())
                BoolType
            }

            "!=" -> {
                ctx.add(Equals())
                ctx.add(Not())
                BoolType
            }

            "&&" -> {
                ctx.add(And())
                BoolType
            }

            "||" -> {
                ctx.add(Or())
                BoolType
            }

            else -> throw IllegalArgumentException("Can't apply operator $operator")
        }
    }
}
