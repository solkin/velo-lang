package compiler.parser

import java.io.InputStream

class StreamInput(val input: InputStream) : Input {

    private var line = 0
    private var col = 0
    private var next: Char? = null

    override fun peek(): Char {
        return next ?: nextChar()
    }

    override fun next(): Char {
        val ch = next ?: nextChar()
        when (ch) {
            Character.MIN_VALUE -> {}
            '\n', '\r' -> {
                line++
                col = 0
            }

            else -> {
                col++
            }
        }
        next = null
        return ch
    }

    override fun eof(): Boolean {
        return peek() == Character.MIN_VALUE
    }

    override fun croak(msg: String) {
        throw Error("$msg ($line:$col)")
    }

    override fun mark() {
        input.mark(8)
    }

    override fun reset() {
        input.reset()
    }

    private fun nextChar(): Char {
        val r = input.read()
        val c = if (r == -1) {
            Character.MIN_VALUE
        } else {
            r.toChar()
        }
        next = c
        return c
    }

}