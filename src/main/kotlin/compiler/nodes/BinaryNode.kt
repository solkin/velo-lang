package compiler.nodes

import compiler.Context
import vm.operations.And
import vm.operations.Divide
import vm.operations.Equals
import vm.operations.If
import vm.operations.Minus
import vm.operations.More
import vm.operations.Move
import vm.operations.Multiply
import vm.operations.Or
import vm.operations.Plus
import vm.operations.Push
import vm.operations.Rem
import vm.operations.Swap
import vm.operations.Xor

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
                ctx.add(Swap())
                ctx.add(More())
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
                ctx.add(More())
                ctx.add(If(elseSkip = 2))
                ctx.add(Push(value = false))
                ctx.add(Move(count = 1))
                ctx.add(Push(value = true))
                BoolType
            }

            ">=" -> {
                ctx.add(Swap())
                ctx.add(More())
                ctx.add(If(elseSkip = 2))
                ctx.add(Push(value = false))
                ctx.add(Move(count = 1))
                ctx.add(Push(value = true))
                BoolType
            }

            "!=" -> {
                ctx.add(Equals())
                ctx.add(If(elseSkip = 2))
                ctx.add(Push(value = false))
                ctx.add(Move(count = 1))
                ctx.add(Push(value = true))
                BoolType
            }

            "&" -> {
                if (leftType is BoolType) {
                    ctx.add(If(elseSkip = 3)) // Else skip and push 'false'
                    // Here can be compiled right argument for && implementation
                    ctx.add(If(elseSkip = 2)) // Else skip and push 'false'
                    ctx.add(Push(value = true))
                    ctx.add(Move(count = 1))
                    ctx.add(Push(value = false))
                    BoolType
                } else {
                    ctx.add(And())
                    leftType
                }
            }

            "|" -> {
                if (leftType is BoolType) {
                    ctx.add(If(elseSkip = 1)) // On false skip to second check
                    ctx.add(Move(count = 1)) // Skip second check and push 'true'
                    // Here can be compiled right argument for || implementation
                    ctx.add(If(elseSkip = 2)) // Else skip and push 'false'
                    ctx.add(Push(value = true))
                    ctx.add(Move(count = 1))
                    ctx.add(Push(value = false))
                    BoolType
                } else {
                    ctx.add(Or())
                    leftType
                }
            }

            "^" -> {
                if (leftType is BoolType) {
                    ctx.add(Equals())
                    ctx.add(If(elseSkip = 2))
                    ctx.add(Push(value = false))
                    ctx.add(Move(count = 1))
                    ctx.add(Push(value = true))
                    BoolType
                } else {
                    ctx.add(Xor())
                    leftType
                }
            }

            else -> throw IllegalArgumentException("Can't apply operator $operator")
        }
    }
}
