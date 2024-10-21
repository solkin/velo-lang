package vm

class SimpleParser(
    val operations: List<Operation>
): Parser {

    private val iterator = operations.listIterator()

    override fun next(): Operation? {
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
