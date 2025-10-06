package compiler.parser

import java.io.File

interface DependencyLoader {
    fun load(path: String)
}

class FileInput(val dir: String, val input: InputStack = InputStack()) : DependencyLoader, Input by input {

    override fun load(path: String) {
        File(dir, path).inputStream().use { stream ->
            input.push(input = StringInput(stream.readBytes().toString(Charsets.UTF_8)))
        }
    }

}