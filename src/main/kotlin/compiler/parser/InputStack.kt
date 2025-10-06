package compiler.parser

class InputStack() : Input {

    private val stack = ArrayDeque<Pair<String, Input>>()

    fun push(name: String, input: Input) {
        stack.addLast(element = name to input)
    }

    override fun peek(): Char = stack.last().second.peek()

    override fun next(): Char = stack.last().second.next()

    override fun eof(): Boolean {
        while (stack.size > 1) {
            if (!stack.last().second.eof()) {
                return false
            }
            stack.removeLast()
        }
        return stack.last().second.eof()
    }

    override fun croak(msg: String) {
        val input = stack.last()
        input.second.croak(input.first + ": " + msg)
    }

    override fun mark() = stack.last().second.mark()

    override fun reset() = stack.last().second.reset()
}