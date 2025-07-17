package compiler.parser

import java.io.InputStream

class StreamInput(val input: InputStream) : Input {

    private var line = 0
    private var col = 0
    private var next: Char? = null

    override fun peek(): Char {
        val ch = next ?: nextChar()
        next = ch
        return ch
    }

    override fun next(): Char {
        val ch = next ?: nextChar()
        next = null
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
        return ch
    }

    override fun eof(): Boolean {
        return peek() == Character.MIN_VALUE
    }

    override fun croak(msg: String) {
        throw Error("$msg ($line:$col)")
    }

    override fun mark() {
        input.mark(1024)
    }

    override fun reset() {
        input.reset()
        next = null
    }

    private fun nextChar(): Char {
        val r = input.read()
        val c = if (r == -1) {
            Character.MIN_VALUE
        } else {
            r.toChar()
        }
        return c
    }

}