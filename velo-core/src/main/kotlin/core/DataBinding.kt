package core

import java.lang.invoke.MethodHandle
import java.lang.invoke.MethodHandles

/**
 * Binds a Velo `data class` to its host (JVM) counterpart so values can be
 * marshalled across the native boundary *by value* (a data class is copied,
 * not handed over as an opaque handle like a registered native class).
 *
 * The contract on the host type is deliberately plain — no annotations:
 *   - exactly one public constructor, taking the fields in the same order as
 *     the Velo `data class` declares them (used for Velo → JVM);
 *   - each field readable by a public getter (`getX()` / `isX()`) or a public
 *     field `x`, matched by the Velo field name (used for JVM → Velo).
 *
 * A Kotlin `data class Point(val x: Int, val y: Int)` satisfies both for free.
 */
class DataBinding(
    val veloName: String,
    val jvmClass: Class<*>,
    val ctorHandle: MethodHandle,
    val ctorJvmParams: List<Class<*>>,
) {
    private val readers = HashMap<String, (Any) -> Any?>()

    /** Read field [fieldName] off a host [instance] (getter, `is`-getter, or field). */
    fun read(instance: Any, fieldName: String): Any? =
        readers.getOrPut(fieldName) { resolveReader(fieldName) }.invoke(instance)

    private fun resolveReader(fieldName: String): (Any) -> Any? {
        val cap = fieldName.replaceFirstChar { it.uppercase() }
        val getter = findMethod("get$cap") ?: findMethod("is$cap")
        if (getter != null) return { instance -> getter.invoke(instance) }
        val field = runCatching { jvmClass.getField(fieldName) }.getOrNull()
            ?: throw NativeMappingException(
                listOf("Data binding '$veloName' (${jvmClass.name}) has no getter or public field for '$fieldName'")
            )
        return { instance -> field.get(instance) }
    }

    private fun findMethod(name: String) =
        runCatching { jvmClass.getMethod(name) }.getOrNull()

    companion object {
        fun introspect(veloName: String, jvmClass: Class<*>): DataBinding {
            val ctors = jvmClass.constructors
            if (ctors.size != 1) {
                throw NativeMappingException(
                    listOf(
                        "Data binding '$veloName' (${jvmClass.name}) must have exactly one public " +
                            "constructor, found ${ctors.size}"
                    )
                )
            }
            val ctor = ctors[0]
            return DataBinding(
                veloName = veloName,
                jvmClass = jvmClass,
                ctorHandle = MethodHandles.lookup().unreflectConstructor(ctor),
                ctorJvmParams = ctor.parameterTypes.toList(),
            )
        }
    }
}
