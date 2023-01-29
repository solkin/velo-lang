package parser

interface Input {

    fun peek(): Char
    fun next(): Char
    fun eof(): Boolean
    fun croak(msg: String)

}