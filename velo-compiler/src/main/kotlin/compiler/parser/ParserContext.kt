package compiler.parser

import compiler.nodes.ClassType
import compiler.nodes.GenericType
import compiler.nodes.InterfaceType
import compiler.nodes.NativeClassType
import core.NativeRegistry

/**
 * Parse-time type environment.
 *
 * Velo-declared classes are registered here by [ClassParselet] as parsing
 * progresses. Native (host) classes are not declared in source at all —
 * they resolve through the [nativeRegistry] the program is being compiled
 * against, with the type synthesized from the registry's descriptor on
 * first reference.
 */
class ParserContext(
    private val nativeRegistry: NativeRegistry? = null,
) {
    val classTypes = mutableMapOf<String, ClassType>()
    private val interfaceTypes = mutableMapOf<String, InterfaceType>()
    private val genericTypes = mutableMapOf<String, GenericType>()

    /**
     * stdlib modules to auto-import ahead of the user code, requested during
     * parsing when a construct lowers onto a library type (e.g. `dict` lowers
     * onto std/map). A general dependency set, not a per-type flag: the parser
     * ([compiler.parser.Parser.parse]) prepends each one unless the program
     * imported it explicitly. Insertion-ordered for deterministic output.
     */
    val autoImports: MutableSet<String> = linkedSetOf()

    fun requireModule(module: String) {
        autoImports.add(module)
    }

    // ---- namespaced imports (`import "x" as ns`) ----
    // A namespaced module's top-level names are mangled to `ns$name`, reached
    // from outside as `ns.name`. `currentNamespace`/`currentModuleLocals` are the
    // active module's mangling state during its (isolated) sub-parse.
    private val namespaces = mutableSetOf<String>()
    var currentNamespace: String? = null
    val currentModuleLocals = mutableSetOf<String>()

    /**
     * Whether a declared name should be namespace-mangled. True at a module's top
     * level; a class body sets it false so **methods** keep their plain names
     * (they resolve through the class, not the module namespace).
     */
    var mangleDeclarations = true

    fun registerNamespace(name: String) {
        namespaces.add(name)
    }

    fun isNamespace(name: String): Boolean = name in namespaces

    /** Mangle a name being *declared* at a namespaced module's top level (and record it). */
    fun declareName(name: String): String {
        val ns = currentNamespace ?: return name
        if (!mangleDeclarations) return name
        currentModuleLocals.add(name)
        return "$ns\$$name"
    }

    /** Mangle a *reference* to one of the current namespaced module's own names. */
    fun localRef(name: String): String {
        val ns = currentNamespace ?: return name
        return if (name in currentModuleLocals) "$ns\$$name" else name
    }

    fun registerClass(name: String, type: ClassType) {
        if (classTypes.containsKey(name)) {
            throw IllegalStateException(
                "Class '$name' is already defined — an imported module already declares it. " +
                    "Rename one, or import the other module under a namespace (import \"...\" as x)."
            )
        }
        classTypes[name] = type
    }

    fun isClassType(name: String): Boolean {
        return classTypes.containsKey(name)
    }

    fun getClassType(name: String): ClassType? {
        return classTypes[name]
    }

    fun registerInterface(name: String, type: InterfaceType) {
        interfaceTypes[name] = type
    }

    fun isInterfaceType(name: String): Boolean {
        return interfaceTypes.containsKey(name)
    }

    fun getInterfaceType(name: String): InterfaceType? {
        return interfaceTypes[name]
    }

    /** A Velo class declaration shadows a registered native class. */
    fun isNativeType(name: String): Boolean =
        !classTypes.containsKey(name) && !interfaceTypes.containsKey(name) &&
            nativeRegistry?.isRegistered(name) == true

    fun getNativeType(name: String): NativeClassType? {
        if (!isNativeType(name)) return null
        val descriptor = nativeRegistry?.descriptor(name) ?: return null
        return NativeClassType(descriptor)
    }

    fun registerGenericType(name: String, bound: InterfaceType? = null) {
        genericTypes[name] = GenericType(name, bound)
    }

    fun isGenericType(name: String): Boolean {
        return genericTypes.containsKey(name)
    }

    fun getGenericType(name: String): GenericType? {
        return genericTypes[name]
    }

    fun saveGenericTypes(): Map<String, GenericType> = HashMap(genericTypes)

    fun restoreGenericTypes(saved: Map<String, GenericType>) {
        genericTypes.clear()
        genericTypes.putAll(saved)
    }
}
