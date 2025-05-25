package vm

import utils.SerializedFrame

class SimpleParser(
    frames: List<SerializedFrame>
): Parser {

    private val iterator = frames.listIterator()

    override fun next(): SerializedFrame? {
        return if (eof()) {
            null
        } else {
            iterator.next()
        }
    }

    override fun eof(): Boolean {
        return !iterator.hasNext()
    }

}
