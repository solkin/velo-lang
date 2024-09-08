package nodes

import Environment
import vm2.Operation
import vm2.operations.*

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

    override fun compile(ops: MutableList<Operation>) {
        left.compile(ops)
        right.compile(ops)
        val binOp = when (operator) {
            "+" -> Plus()
            "-" -> Minus()
            "*" -> Multiply()
            "/" -> Divide()
            "<" -> Less()
            ">" -> More()
            "==" -> Equals()
            "<=" -> LessEquals()
            ">=" -> MoreEquals()
            else -> throw IllegalArgumentException("Can't apply operator $operator")
        }
        ops.add(binOp)
    }
}
