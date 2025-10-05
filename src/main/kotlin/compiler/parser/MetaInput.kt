package compiler.parser

class MetaInput(startInput: Input) : Input {

    private val stack = ArrayDeque<Input>().apply { add(startInput) }

    public fun push(input: Input) {
        stack.addLast(input)
    }

    override fun peek(): Char = stack.last().peek()

    override fun next(): Char = stack.last().next()

    override fun eof(): Boolean {
        while (stack.size > 1) {
            if (!stack.last().eof()) {
                return false
            }
            stack.removeLast()
        }
        return stack.last().eof()
    }

    override fun croak(msg: String) = stack.last().croak(msg)

    override fun mark() = stack.last().mark()

    override fun reset() = stack.last().reset()
}