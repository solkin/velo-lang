package nodes

import Environment

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
            "==" -> BoolType(a == b)
            "!=" -> BoolType(a != b)
            else -> throw IllegalArgumentException("Can't apply operator $op")
        }
    }
}
