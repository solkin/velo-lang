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

class SimpleInput(
    val deps: Map<String, String>,
    val input: InputStack = InputStack()
) : DependencyLoader, Input by input {

    override fun load(name: String) {
        val depSrc = deps[name] ?: throw Exception("Dependency $name not found")
        input.push(name, input = StringInput(depSrc))
    }

}