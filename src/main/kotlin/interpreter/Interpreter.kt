package interpreter

import parser.*

class Interpreter {

    fun evaluate(exp: Node, env: Environment): Node {
        return when (exp) {
            is NumNode,
            is StrNode,
            is BoolNode -> exp

            is VarNode -> env.get(exp.name)
            is AssignNode -> {
                if (exp.left !is VarNode) throw IllegalArgumentException("Cannot assign to " + exp.left)
                env.set(exp.left.name, evaluate(exp.right, env))
            }

            is BinaryNode -> applyOp(
                exp.operator,
                evaluate(exp.left, env),
                evaluate(exp.right, env)
            )

            is LambdaNode -> RunnableNode(func = makeLambda(env, exp))
            is IfNode -> {
                val cond = evaluate(exp.condNode, env)
                if (cond != FALSE) return evaluate(exp.thenNode, env)
                exp.elseNode?.let { evaluate(exp.elseNode, env) } ?: FALSE
            }

            is ProgramNode -> {
                var v: Node = FALSE
                exp.prog.forEach { v = evaluate(it, env) }
                v
            }

            is CallNode -> {
                val func = evaluate(exp.func, env) as RunnableNode
                val args = exp.args.map {
                    evaluate(it, env)
                }
                func.func.invoke(args)
            }

            else -> throw IllegalArgumentException("I don't know how to evaluate " + exp.type)
        }
    }

    private fun makeLambda(env: Environment, exp: LambdaNode) = fun(args: List<Node>): Node {
        val scope = env.extend()
        exp.vars.forEachIndexed { i, s ->
            scope.def(s, if (i < args.size) args[i] else FALSE)
        }
        return evaluate(exp.body, scope)
    }

    private fun applyOp(op: String, a: Node, b: Node): Node {
        fun num(n: Node): Double {
            return (n as? NumNode)?.value ?: throw IllegalArgumentException("Expected number but got " + n.type)
        }

        fun bool(n: Node): Boolean {
            return (n as? BoolNode)?.value ?: throw IllegalArgumentException("Expected boolean but got " + n.type)
        }

        fun div(n: Node): Double {
            val v = num(n)
            if (v == 0.0) {
                throw IllegalArgumentException("Division by zero")
            }
            return v
        }
        return when (op) {
            "+" -> NumNode(num(a) + num(b))
            "-" -> NumNode(num(a) - num(b))
            "*" -> NumNode(num(a) * num(b))
            "/" -> NumNode(num(a) / div(b))
            "%" -> NumNode(num(a) % div(b))
            "&&" -> BoolNode(bool(a) && bool(b))
            "||" -> BoolNode(if (bool(a)) bool(a) else bool(b))
            "<" -> BoolNode(num(a) < num(b))
            ">" -> BoolNode(num(a) > num(b))
            "<=" -> BoolNode(num(a) <= num(b))
            ">=" -> BoolNode(num(a) >= num(b))
            "==" -> BoolNode(a == b)
            "!=" -> BoolNode(a != b)
            else -> throw IllegalArgumentException("Can't apply operator $op")
        }
    }

}