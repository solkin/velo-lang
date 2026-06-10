package core

import java.lang.invoke.MethodHandle

/**
 * One linked native pool entry: the serialized [NativeRef] joined with the
 * registry's resolved [MethodHandle] and JVM parameter classes. Produced by
 * [NativeLinker.link] before the program runs; indexed directly by the
 * `Op.NativeCall` instruction.
 */
class BoundNative(
    val ref: NativeRef,
    val handle: MethodHandle,
    val jvmParams: List<Class<*>>,
) {
    val isConstructor: Boolean get() = ref.kind == NativeRef.Kind.CONSTRUCTOR
}

/**
 * Resolves a program's native pool against a [NativeRegistry] — the
 * load-time "link" step.
 *
 * All entries are checked before execution starts, and *every* problem is
 * reported in one [NativeMappingException]: for an embedding host this is
 * the complete diff between what the script expects and what the host
 * provides, instead of a sequence of mid-run failures.
 */
object NativeLinker {

    fun link(pool: List<NativeRef>, registry: NativeRegistry): Array<BoundNative> {
        val problems = mutableListOf<String>()
        val bound = pool.map { ref ->
            val descriptor = try {
                registry.descriptor(ref.className)
            } catch (ex: NativeMappingException) {
                problems += ex.problems
                null
            }
            if (descriptor == null) {
                if (!problems.any { it.contains("'${ref.className}'") }) {
                    problems += "native class '${ref.className}' is not registered in this runtime"
                }
                return@map null
            }
            when (ref.kind) {
                NativeRef.Kind.CONSTRUCTOR -> {
                    if (descriptor.ctorParams != ref.params) {
                        problems += "constructor of '${ref.className}': program expects " +
                            "(${ref.params.joinToString()}), host provides (${descriptor.ctorParams.joinToString()})"
                        null
                    } else {
                        BoundNative(ref, descriptor.ctorHandle, descriptor.ctorJvmParams)
                    }
                }
                NativeRef.Kind.METHOD -> {
                    val method = descriptor.methods[ref.methodName]
                    when {
                        method == null -> {
                            problems += "'${ref.className}.${ref.methodName}' is missing on the host class " +
                                descriptor.jvmClass.name
                            null
                        }
                        method.params != ref.params || method.returns != ref.returns -> {
                            problems += "'${ref.className}.${ref.methodName}': program expects " +
                                "(${ref.params.joinToString()}): ${ref.returns}, host provides " +
                                "(${method.params.joinToString()}): ${method.returns}"
                            null
                        }
                        else -> BoundNative(ref, method.handle, method.jvmParams)
                    }
                }
            }
        }
        if (problems.isNotEmpty()) {
            throw NativeMappingException(problems)
        }
        return bound.map { it!! }.toTypedArray()
    }
}
