package vm

import utils.SerializedFrame

interface Parser {
    fun next(): SerializedFrame?
    fun eof(): Boolean
}
