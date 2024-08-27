package vm2

interface Parser {

    fun next(): Operation?

    fun eof(): Boolean

}
