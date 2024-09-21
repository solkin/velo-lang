package vm

interface Parser {

    fun next(): Operation?

    fun eof(): Boolean

}
