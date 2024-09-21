package compiler.nodes

import compiler.CompilerContext
import compiler.Environment
import vm2.operations.And
import vm2.operations.Divide
import vm2.operations.Equals
import vm2.operations.Less
import vm2.operations.LessEquals
import vm2.operations.Minus
import vm2.operations.More
import vm2.operations.MoreEquals
import vm2.operations.Multiply
import vm2.operations.Or
import vm2.operations.Plus
import vm2.operations.Rem

data class BinaryNode(
    val operator: String,
    val left: Node,
    val right: Node,
) : Node() {
    override fun evaluate(env: Environment<Value<*>>): Value<*> = applyOp(
        operator,
        left.evaluate(env),
        right.evaluate(env)
    )

    private fun applyOp(op: String, a: Value<*>, b: Value<*>): Value<*> {
        return when (op) {
            "+" -> a + b
            "-" -> a - b
            "*" -> a * b
            "/" -> a / b
            "%" -> a % b
            "&&" -> BoolValue(a.asBool() && b.asBool())
            "||" -> BoolValue(if (a.asBool()) a.asBool() else b.asBool())
            "<" -> BoolValue(a < b)
            ">" -> BoolValue(a > b)
            "<=" -> BoolValue(a <= b)
            ">=" -> BoolValue(a >= b)
            "==" -> BoolValue(a.value() == b.value())
            "!=" -> BoolValue(a.value() != b.value())
            else -> throw IllegalArgumentException("Can't apply operator $op")
        }
    }

    override fun compile(ctx: CompilerContext): Type {
        val leftType = left.compile(ctx)
        val rightType = right.compile(ctx)
        if (leftType != rightType) {
            throw IllegalArgumentException("Binary operation with different types $leftType [$operator] $rightType")
        }
        val binOp = when (operator) {
            "+" -> Plus()
            "-" -> Minus()
            "*" -> Multiply()
            "/" -> Divide()
            "%" -> Rem()
            "<" -> Less()
            ">" -> More()
            "==" -> Equals()
            "<=" -> LessEquals()
            ">=" -> MoreEquals()
            "&&" -> And()
            "||" -> Or()
            else -> throw IllegalArgumentException("Can't apply operator $operator")
        }
        ctx.add(binOp)
        return leftType
    }
}
