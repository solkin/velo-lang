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
            "+" -> if (a.isNum() && b.isNum()) NumType(num(a) + num(b)) else StrType(a.value().toString() + b.value().toString())
            "-" -> NumType(num(a) - num(b))
            "*" -> NumType(num(a) * num(b))
            "/" -> NumType(num(a) / div(b))
            "%" -> NumType(num(a) % div(b))
            "&&" -> BoolType(bool(a) && bool(b))
            "||" -> BoolType(if (bool(a)) bool(a) else bool(b))
            "<" -> BoolType(num(a) < num(b))
            ">" -> BoolType(num(a) > num(b))
            "<=" -> BoolType(num(a) <= num(b))
            ">=" -> BoolType(num(a) >= num(b))
            "==" -> BoolType(a.value() == b.value())
            "!=" -> BoolType(a.value() != b.value())
            else -> throw IllegalArgumentException("Can't apply operator $op")
        }
    }
}

private fun Type<*>.isNum() = this is NumType

private fun num(n: Type<*>): Double {
    return (n as? NumType)?.value() ?: throw IllegalArgumentException("Expected number but got $n")
}

private fun bool(n: Type<*>): Boolean {
    return (n as? BoolType)?.value() ?: throw IllegalArgumentException("Expected boolean but got $n")
}

private fun div(n: Type<*>): Double {
    val v = num(n)
    if (v == 0.0) {
        throw IllegalArgumentException("Division by zero")
    }
    return v
}