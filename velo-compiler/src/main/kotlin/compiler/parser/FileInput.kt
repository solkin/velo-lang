package compiler.parser

import java.io.File

/** A resolved module ready to parse: its source [text] and the [dir] its own imports resolve against. */
class Module(val text: String, val dir: File?)

interface DependencyLoader {
    /** Load the root program onto the input stack (the file the parser reads from). */
    fun load(name: String)

    fun isLoaded(name: String): Boolean = false

    /**
     * Resolve an `import` [name] relative to [fromDir] (the importing file's
     * directory; `null` for the root / classpath). Returns the module to parse,
     * `null` if it was already imported this compilation (dedup by canonical
     * path — so the same file reached by different spellings loads once), and
     * throws if it cannot be found.
     */
    fun module(name: String, fromDir: File?): Module? = null
}

/**
 * Base dependency loader. Guarantees for every loader:
 *  - the `.vel` extension is optional (`import "std/map"`);
 *  - `std/` names resolve to the bundled standard library (classpath);
 *  - file-system imports resolve **relative to the importing file**;
 *  - each module loads at most once, keyed by canonical path.
 */
abstract class SourceLoader(val input: InputStack = InputStack()) : DependencyLoader, Input by input {

    private val loaded = HashSet<String>()

    protected class Resolved(val key: String, val text: String, val dir: File?)

    /** Push the root program (the parser tokenizes it directly). */
    override fun load(name: String) {
        val root = resolve(name, fromDir = null) ?: throw Exception("Cannot resolve '$name'")
        loaded.add(root.key)
        input.push(name = name, input = StringInput(root.text), dir = root.dir)
    }

    override fun isLoaded(name: String): Boolean = withVel(name) in loaded

    override fun module(name: String, fromDir: File?): Module? {
        val resolved = resolve(name, fromDir) ?: throw Exception("Cannot resolve import '$name'")
        if (!loaded.add(resolved.key)) return null
        return Module(resolved.text, resolved.dir)
    }

    /** [fromDir] is the importing file's directory (`null` for the root/classpath base). */
    protected open fun resolve(name: String, fromDir: File?): Resolved? {
        val n = withVel(name)
        if (n.startsWith(STDLIB_PREFIX)) {
            SourceLoader::class.java.getResourceAsStream("/$n")?.use { stream ->
                return Resolved(key = n, text = stream.readBytes().toString(Charsets.UTF_8), dir = null)
            }
        }
        return null
    }

    companion object {
        const val STDLIB_PREFIX = "std/"

        /** The stdlib module dict syntax lowers onto (auto-imported). */
        const val STDLIB_MAP = STDLIB_PREFIX + "map"

        /** The stdlib string module; `str.int()` lives here, auto-imported where `.int()` is used. */
        const val STDLIB_STR = STDLIB_PREFIX + "str"

        fun withVel(name: String): String = if (name.endsWith(".vel")) name else "$name.vel"
    }
}

/** Loads imports from the file system, relative to the importing file (root falls back to [dir]). */
class FileInput(val dir: String?, input: InputStack = InputStack()) : SourceLoader(input) {

    override fun resolve(name: String, fromDir: File?): Resolved? {
        super.resolve(name, fromDir)?.let { return it }
        val base = fromDir ?: File(dir ?: ".")
        val file = File(base, withVel(name)).canonicalFile
        if (!file.isFile) return null
        return Resolved(key = file.path, text = file.readText(Charsets.UTF_8), dir = file.parentFile)
    }
}

/** Loads imports from a predefined name → source map (tests / embedding). */
class SimpleInput(
    val deps: Map<String, String>,
    input: InputStack = InputStack()
) : SourceLoader(input) {

    override fun resolve(name: String, fromDir: File?): Resolved? {
        super.resolve(name, fromDir)?.let { return it }
        val text = deps[name] ?: deps[withVel(name)] ?: return null
        return Resolved(key = withVel(name), text = text, dir = null)
    }
}
