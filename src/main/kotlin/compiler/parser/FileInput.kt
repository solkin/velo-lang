package compiler.parser

import java.io.File

interface DependencyLoader {
    fun load(name: String)
}

class FileInput(val dir: String, val input: InputStack = InputStack()) : DependencyLoader, Input by input {

    override fun load(name: String) {
        File(dir, name).inputStream().use { stream ->
            input.push(name, input = StringInput(stream.readBytes().toString(Charsets.UTF_8)))
        }
    }

}