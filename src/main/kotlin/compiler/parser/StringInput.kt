package compiler.parser

import java.lang.Character.MIN_VALUE

class StringInput(private val str: String) : Input {

    private var pos = 0
    private var line = 0
    private var col = 0

    override fun peek(): Char {
        return getChar(pos)
    }

    override fun next(): Char {
        val ch = getChar(pos++)
        if (ch == '\n' || ch == '\r') {
            line++
            col = 0
        } else {
            col++
        }
        return ch
    }

    override fun eof(): Boolean {
        return peek() == MIN_VALUE
    }

    override fun croak(msg: String) {
        throw Error("$msg ($line:$col)")
    }

    private fun getChar(p: Int): Char {
        return if (p < str.length) str[p] else MIN_VALUE
    }

}