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

        // Logical && / || on booleans: the right operand is compiled lazily so it is only
        // evaluated when it can still change the result (short-circuit), and so the stack is
        // left holding exactly one boolean. The previous codegen compiled the right operand
        // eagerly above and then ran an If-dance that assumed it had not — corrupting the stack
        // (a stray operand was left behind whenever a sub-expression took the skip branch),
        // which silently produced wrong results for nested boolean expressions.
        // `&&` / `||` are the logical operators; `&` / `|` stay bitwise on ints
        // but short-circuit when both sides are bool (kept as aliases for now).
        if (leftType is BoolType && (operator == "&" || operator == "|" || operator == "&&" || operator == "||")) {
            return compileShortCircuit(ctx)
        }

        val rightType = right.compile(ctx)
        // A `data class` has built-in by-value equality; `==`/`!=` lower to the
        // deep [Op.Equals] comparison without needing an operator overload.
        if (leftType is ClassType && leftType.isData && (operator == "==" || operator == "!=")) {
            if (!leftType.sameAs(rightType)) {
                throw IllegalArgumentException("Cannot compare '${leftType.name}' with '${rightType.log()}'")
            }
            ctx.add(Op.Equals)
            if (operator == "!=") {
                ctx.add(Op.If(elseSkip = 2))
                ctx.add(Op.Push(value = false))
                ctx.add(Op.Move(count = 1))
                ctx.add(Op.Push(value = true))
            }
            return BoolType
        }
        if (leftType is ClassType) {
            val opName = "op@$operator"
            val prop = ClassElementProp(opName)
            try {
                return prop.compile(leftType, args = listOf(rightType), ctx)
            } catch (e: IllegalArgumentException) {
                throw IllegalArgumentException("Operator '$operator' is not defined for class '${leftType.name}'")
            }
        }
        // Mixed numeric operands (e.g. int + float) are allowed: the VM promotes
        // by value at runtime, and the static result is the wider of the two.
        val leftRank = numericRank(leftType)
        val rightRank = numericRank(rightType)
        val bothNumeric = leftRank != null && rightRank != null
        val numWider = if (bothNumeric && leftRank!! < rightRank!!) rightType else leftType
        if (!bothNumeric && !leftType.sameAs(rightType)) {
            if (operator == "+" && (leftType is StringType || rightType is StringType)) {
                val other = if (leftType is StringType) rightType else leftType
                throw IllegalArgumentException(
                    "Cannot join str with ${other.log()}. Convert the value with .str() " +
                        "(e.g. \"count = \" + n.str()) or use string interpolation (\"count = \$n\")."
                )
            }
            throw IllegalArgumentException("Binary operation with different types $leftType [$operator] $rightType")
        }
        return when (operator) {
            "+" -> {
                if (leftType is StringType) {
                    ctx.add(Op.StrCon)
                } else {
                    ctx.add(Op.Add)
                }
                numWider
            }

            "-" -> {
                ctx.add(Op.Sub)
                numWider
            }

            "*" -> {
                ctx.add(Op.Mul)
                numWider
            }

            "/" -> {
                ctx.add(Op.Div)
                numWider
            }

            "%" -> {
                ctx.add(Op.Rem)
                numWider
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

    /**
     * Emit short-circuit code for a boolean `&` / `|`. The left operand is already on the
     * stack; the right operand is compiled here (lazily) and jump distances are backpatched
     * once its length is known. Every path consumes the operands and leaves exactly one bool.
     */
    private fun compileShortCircuit(ctx: Context): Type {
        if (operator == "&" || operator == "&&") {
            //   [If ?]  <right>  [Move 1]  [Push false]
            // left true  → fall through, evaluate right, its value is the result (skip Push false)
            // left false → skip right + Move, land on Push false
            val ifIndex = ctx.size()
            ctx.add(Op.If(elseSkip = 0))
            val before = ctx.size()
            val rightType = right.compile(ctx)
            requireBool(rightType)
            ctx.add(Op.Move(count = 1))
            ctx.replace(ifIndex, Op.If(elseSkip = ctx.size() - before))
            ctx.add(Op.Push(value = false))
        } else {
            //   [If 1]  [Move ?]  <right>  [Move 1]  [Push true]
            // left true  → run Move ?, skipping right + its Move, land on Push true
            // left false → skip the Move, evaluate right, its value is the result (skip Push true)
            val ifIndex = ctx.size()
            ctx.add(Op.If(elseSkip = 1))
            val moveIndex = ctx.size()
            ctx.add(Op.Move(count = 0))
            val before = ctx.size()
            val rightType = right.compile(ctx)
            requireBool(rightType)
            ctx.add(Op.Move(count = 1))
            ctx.replace(moveIndex, Op.Move(count = ctx.size() - before))
            ctx.add(Op.Push(value = true))
        }
        return BoolType
    }

    private fun requireBool(type: Type) {
        if (type !is BoolType) {
            throw IllegalArgumentException("Operator '$operator' expects a bool right operand, got ${type.log()}")
        }
    }
}
