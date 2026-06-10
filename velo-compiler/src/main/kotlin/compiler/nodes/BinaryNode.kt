package compiler.nodes

import core.Op

import compiler.Context

data class BinaryNode(
    val operator: String,
    val left: Node,
    val right: Node,
) : Node() {
    override fun compile(ctx: Context): Type {
        val leftType = left.compile(ctx)
        val rightType = right.compile(ctx)
        if (leftType is ClassType) {
            val opName = "op@$operator"
            val prop = ClassElementProp(opName)
            try {
                return prop.compile(leftType, args = listOf(rightType), ctx)
            } catch (e: IllegalArgumentException) {
                throw IllegalArgumentException("Operator '$operator' is not defined for class '${leftType.name}'")
            }
        }
        if (!leftType.sameAs(rightType)) {
            throw IllegalArgumentException("Binary operation with different types $leftType [$operator] $rightType")
        }
        return when (operator) {
            "+" -> {
                if (leftType is StringType) {
                    ctx.add(Op.StrCon)
                } else {
                    ctx.add(Op.Add)
                }
                leftType
            }

            "-" -> {
                ctx.add(Op.Sub)
                leftType
            }

            "*" -> {
                ctx.add(Op.Mul)
                leftType
            }

            "/" -> {
                ctx.add(Op.Div)
                leftType
            }

            "%" -> {
                ctx.add(Op.Rem)
                leftType
            }

            "<" -> {
                ctx.add(Op.Swap)
                ctx.add(Op.More)
                BoolType
            }

            ">" -> {
                ctx.add(Op.More)
                BoolType
            }

            "==" -> {
                ctx.add(Op.Equals)
                BoolType
            }

            "<=" -> {
                ctx.add(Op.More)
                ctx.add(Op.If(elseSkip = 2))
                ctx.add(Op.Push(value = false))
                ctx.add(Op.Move(count = 1))
                ctx.add(Op.Push(value = true))
                BoolType
            }

            ">=" -> {
                ctx.add(Op.Swap)
                ctx.add(Op.More)
                ctx.add(Op.If(elseSkip = 2))
                ctx.add(Op.Push(value = false))
                ctx.add(Op.Move(count = 1))
                ctx.add(Op.Push(value = true))
                BoolType
            }

            "!=" -> {
                ctx.add(Op.Equals)
                ctx.add(Op.If(elseSkip = 2))
                ctx.add(Op.Push(value = false))
                ctx.add(Op.Move(count = 1))
                ctx.add(Op.Push(value = true))
                BoolType
            }

            "&" -> {
                if (leftType is BoolType) {
                    ctx.add(Op.If(elseSkip = 3)) // Else skip and push 'false'
                    // Here can be compiled right argument for && implementation
                    ctx.add(Op.If(elseSkip = 2)) // Else skip and push 'false'
                    ctx.add(Op.Push(value = true))
                    ctx.add(Op.Move(count = 1))
                    ctx.add(Op.Push(value = false))
                    BoolType
                } else {
                    ctx.add(Op.And)
                    leftType
                }
            }

            "|" -> {
                if (leftType is BoolType) {
                    ctx.add(Op.If(elseSkip = 1)) // On false skip to second check
                    ctx.add(Op.Move(count = 1)) // Skip second check and push 'true'
                    // Here can be compiled right argument for || implementation
                    ctx.add(Op.If(elseSkip = 2)) // Else skip and push 'false'
                    ctx.add(Op.Push(value = true))
                    ctx.add(Op.Move(count = 1))
                    ctx.add(Op.Push(value = false))
                    BoolType
                } else {
                    ctx.add(Op.Or)
                    leftType
                }
            }

            "^" -> {
                if (leftType is BoolType) {
                    ctx.add(Op.Equals)
                    ctx.add(Op.If(elseSkip = 2))
                    ctx.add(Op.Push(value = false))
                    ctx.add(Op.Move(count = 1))
                    ctx.add(Op.Push(value = true))
                    BoolType
                } else {
                    ctx.add(Op.Xor)
                    leftType
                }
            }

            else -> throw IllegalArgumentException("Can't apply operator $operator")
        }
    }
}
