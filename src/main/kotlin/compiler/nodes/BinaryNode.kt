package compiler.nodes

import compiler.Context
import compiler.Environment
import vm.operations.And
import vm.operations.Divide
import vm.operations.Equals
import vm.operations.Less
import vm.operations.LessEquals
import vm.operations.Minus
import vm.operations.More
import vm.operations.MoreEquals
import vm.operations.Multiply
import vm.operations.Or
import vm.operations.Plus
import vm.operations.Rem

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

    override fun compile(ctx: Context): Type {
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
