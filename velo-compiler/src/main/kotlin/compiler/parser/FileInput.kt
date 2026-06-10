package compiler.parser

import java.io.File

interface DependencyLoader {
    fun load(name: String)
    fun isLoaded(name: String): Boolean = false
}

/**
 * Base dependency loader: resolves `include` names to source text and
 * pushes it onto the input stack.
 *
 * Two guarantees shared by every loader:
 *  - each dependency name is loaded at most once per compilation
 *    (so the stdlib can be both auto-included and included explicitly);
 *  - names under `lang/` resolve to the standard library bundled with
 *    the compiler (classpath resources), independent of the file system.
 */
abstract class SourceLoader(val input: InputStack = InputStack()) : DependencyLoader, Input by input {

    private val loaded = HashSet<String>()

    override fun load(name: String) {
        if (!loaded.add(name)) return
        val source = resolve(name) ?: throw Exception("Dependency $name not found")
        input.push(name, input = StringInput(source))
    }

    override fun isLoaded(name: String): Boolean = name in loaded

    protected open fun resolve(name: String): String? {
        if (name.startsWith(STDLIB_PREFIX)) {
            SourceLoader::class.java.getResourceAsStream("/$name")?.use { stream ->
                return stream.readBytes().toString(Charsets.UTF_8)
            }
        }
        return null
    }

    companion object {
        const val STDLIB_PREFIX = "lang/"

        /** The stdlib module dict syntax lowers onto (auto-included). */
        const val STDLIB_MAP = STDLIB_PREFIX + "map.vel"
    }
}

/**
 * Loads dependencies from the file system, relative to [dir];
 * `lang/` names come from the bundled stdlib.
 */
class FileInput(val dir: String?, input: InputStack = InputStack()) : SourceLoader(input) {

    override fun resolve(name: String): String? {
        super.resolve(name)?.let { return it }
        val file = File(dir, name)
        if (!file.isFile) return null
        return file.inputStream().use { stream ->
            stream.readBytes().toString(Charsets.UTF_8)
        }
    }

}

/**
 * Loads dependencies from a predefined name → source map;
 * `lang/` names come from the bundled stdlib.
 */
class SimpleInput(
    val deps: Map<String, String>,
    input: InputStack = InputStack()
) : SourceLoader(input) {

    override fun resolve(name: String): String? {
        return deps[name] ?: super.resolve(name)
    }

}
