package compiler.parser

import compiler.nodes.ClassType
import compiler.nodes.GenericType
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
    private val genericTypes = mutableMapOf<String, GenericType>()

    /**
     * Set when the program uses `dict` syntax. Dict lowers onto the stdlib
     * Map class, which [compiler.parser.Parser.parse] then pulls in
     * automatically.
     */
    var dictUsed = false

    fun registerClass(name: String, type: ClassType) {
        classTypes[name] = type
    }

    fun isClassType(name: String): Boolean {
        return classTypes.containsKey(name)
    }

    fun getClassType(name: String): ClassType? {
        return classTypes[name]
    }

    /** A Velo class declaration shadows a registered native class. */
    fun isNativeType(name: String): Boolean =
        !classTypes.containsKey(name) && nativeRegistry?.isRegistered(name) == true

    fun getNativeType(name: String): NativeClassType? {
        if (!isNativeType(name)) return null
        val descriptor = nativeRegistry?.descriptor(name) ?: return null
        return NativeClassType(descriptor)
    }

    fun registerGenericType(name: String) {
        genericTypes[name] = GenericType(name)
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
