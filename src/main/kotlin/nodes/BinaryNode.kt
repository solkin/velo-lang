package nodes

import CompilerContext
import Environment
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
    override fun evaluate(env: Environment<Type<*>>): Type<*> = applyOp(
        operator,
        left.evaluate(env),
        right.evaluate(env)
    )

    private fun applyOp(op: String, a: Type<*>, b: Type<*>): Type<*> {
        return when (op) {
            "+" -> a + b
            "-" -> a - b
            "*" -> a * b
            "/" -> a / b
            "%" -> a % b
            "&&" -> BoolType(a.asBool() && b.asBool())
            "||" -> BoolType(if (a.asBool()) a.asBool() else b.asBool())
            "<" -> BoolType(a < b)
            ">" -> BoolType(a > b)
            "<=" -> BoolType(a <= b)
            ">=" -> BoolType(a >= b)
            "==" -> BoolType(a.value() == b.value())
            "!=" -> BoolType(a.value() != b.value())
            else -> throw IllegalArgumentException("Can't apply operator $op")
        }
    }

    override fun compile(ctx: CompilerContext) {
        left.compile(ctx)
        right.compile(ctx)
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
    }
}
