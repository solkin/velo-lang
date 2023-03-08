package nodes

import Environment

data class BinaryNode(
    val operator: String,
    val left: Node,
    val right: Node,
) : Node() {
    override fun evaluate(env: Environment<Any>): Any = applyOp(
        operator,
        left.evaluate(env),
        right.evaluate(env)
    )

    private fun applyOp(op: String, a: Any, b: Any): Any {
        return when (op) {
            "+" -> if (a.isNum() && b.isNum()) (num(a) + num(b)) else (a.toString() + b.toString())
            "-" -> (num(a) - num(b))
            "*" -> (num(a) * num(b))
            "/" -> (num(a) / div(b))
            "%" -> (num(a) % div(b))
            "&&" -> (bool(a) && bool(b))
            "||" -> (if (bool(a)) bool(a) else bool(b))
            "<" -> (num(a) < num(b))
            ">" -> (num(a) > num(b))
            "<=" -> (num(a) <= num(b))
            ">=" -> (num(a) >= num(b))
            "==" -> (a == b)
            "!=" -> (a != b)
            else -> throw IllegalArgumentException("Can't apply operator $op")
        }
    }
}

private fun Any.isNum() = this is Double

private fun num(n: Any): Double {
    return (n as? Double) ?: throw IllegalArgumentException("Expected number but got $n")
}

private fun bool(n: Any): Boolean {
    return (n as? Boolean) ?: throw IllegalArgumentException("Expected boolean but got $n")
}

private fun div(n: Any): Double {
    val v = num(n)
    if (v == 0.0) {
        throw IllegalArgumentException("Division by zero")
    }
    return v
}