package compiler.parser

object Precedence {
    const val ASSIGNMENT = 1
    const val OR = 2
    const val AND = 3
    const val XOR = 4
    const val COMPARISON = 7
    const val ADDITIVE = 10
    const val MULTIPLICATIVE = 20
    const val CALL = 30
    const val INDEX = 30
    const val PROPERTY = 30
    const val UNARY = 40
}
