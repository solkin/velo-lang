package compiler.parser

import java.io.File

class InputStack() : Input {

    private class Entry(val name: String, val input: Input, val dir: File?)

    private val stack = ArrayDeque<Entry>()

    /**
     * Push a source onto the stack. [dir] is the directory its own imports
     * resolve against (the file's parent, or `null` for classpath/stdlib
     * sources) — so an import is resolved relative to the file that wrote it,
     * not the root program.
     */
    fun push(name: String, input: Input, dir: File? = null): InputStack {
        stack.addLast(Entry(name, input, dir))
        return this
    }

    /** The directory the currently-parsed source resolves its imports against. */
    fun currentDir(): File? = stack.lastOrNull()?.dir

    override fun peek(): Char = stack.last().input.peek()

    override fun next(): Char = stack.last().input.next()

    override fun eof(): Boolean {
        while (stack.size > 1) {
            if (!stack.last().input.eof()) {
                return false
            }
            stack.removeLast()
        }
        return stack.last().input.eof()
    }

    override fun croak(msg: String) {
        val entry = stack.last()
        entry.input.croak(entry.name + ": " + msg)
    }

    override fun mark() = stack.last().input.mark()

    override fun reset() = stack.last().input.reset()
}
