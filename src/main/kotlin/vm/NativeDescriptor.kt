package vm

import vm.actors.VeloFunction
import java.lang.invoke.MethodHandle
import java.lang.invoke.MethodHandles
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type as JType

/**
 * Stable, serializable reference to one native entry point — what the
 * compiler interns into the program's native pool and what `.vbc` stores.
 *
 * Keyed by the *Velo* class name (not the JVM FQN): the registry maps the
 * Velo name to a host class at link time, so the same bytecode links
 * against different host implementations (an Android `Http` vs a server
 * `Http`). [params]/[returns] carry the full Velo-visible signature so the
 * linker can verify the host still matches what the program was compiled
 * against — and fail at load, not mid-execution.
 */
data class NativeRef(
    val kind: Kind,
    val className: String,
    val methodName: String,
    val params: List<VmType>,
    val returns: VmType,
) {
    enum class Kind { CONSTRUCTOR, METHOD }

    override fun toString(): String = when (kind) {
        Kind.CONSTRUCTOR -> "new $className(${params.joinToString()})"
        Kind.METHOD -> "$className.$methodName(${params.joinToString()}): $returns"
    }
}

/**
 * One native method as seen from Velo: the mapped signature plus the
 * pre-resolved [MethodHandle] to invoke it with.
 */
class NativeMethodDescriptor(
    val name: String,
    val params: List<VmType>,
    val returns: VmType,
    val jvmParams: List<Class<*>>,
    val handle: MethodHandle,
)

/**
 * Everything Velo needs to know about one registered host class, built by
 * reflection **once** (lazily, at first compile/link use — so registration
 * order between mutually-referencing classes does not matter) and cached in
 * the [NativeRegistry].
 *
 * Introspection rules — deliberately rigid so host classes stay plain
 * Kotlin/Java with zero annotations:
 *   - exactly one public constructor;
 *   - every public declared (non-inherited, non-synthetic) method is
 *     exposed; method names must be unique — Velo has no overloads;
 *   - every signature must be expressible in Velo types, see [mapJvmType].
 * Violations throw [NativeMappingException] listing every problem at once.
 */
class NativeClassDescriptor(
    val veloName: String,
    val jvmClass: Class<*>,
    val ctorParams: List<VmType>,
    val ctorJvmParams: List<Class<*>>,
    val ctorHandle: MethodHandle,
    val methods: Map<String, NativeMethodDescriptor>,
) {

    fun constructorRef(): NativeRef = NativeRef(
        kind = NativeRef.Kind.CONSTRUCTOR,
        className = veloName,
        methodName = "",
        params = ctorParams,
        returns = VmType.Class(veloName),
    )

    fun methodRef(name: String): NativeRef {
        val m = methods[name] ?: throw NativeMappingException(
            listOf("Native class '$veloName' has no method '$name'")
        )
        return NativeRef(
            kind = NativeRef.Kind.METHOD,
            className = veloName,
            methodName = name,
            params = m.params,
            returns = m.returns,
        )
    }

    companion object {

        fun introspect(veloName: String, jvmClass: Class<*>, registry: NativeRegistry): NativeClassDescriptor {
            val problems = mutableListOf<String>()
            val lookup = MethodHandles.lookup()

            val ctors = jvmClass.constructors
            if (ctors.size != 1) {
                problems += "Native class '$veloName' (${jvmClass.name}) must have exactly one public " +
                    "constructor, found ${ctors.size}"
            }
            val ctor = ctors.firstOrNull()
            val ctorParams = ctor?.parameterTypes?.mapIndexed { i, p ->
                mapJvmType(p, ctor.genericParameterTypes.getOrNull(i), registry)
                    ?: VmType.Any.also {
                        problems += "Native class '$veloName' constructor parameter #${i + 1} " +
                            "has unmappable type ${p.name}"
                    }
            } ?: emptyList()

            val methods = LinkedHashMap<String, NativeMethodDescriptor>()
            val candidates = jvmClass.declaredMethods
                .filter { Modifier.isPublic(it.modifiers) && !it.isSynthetic && !it.isBridge }
                .sortedBy { it.name }
            for (method in candidates) {
                if (methods.containsKey(method.name)) {
                    problems += "Native class '$veloName' method '${method.name}' is overloaded — " +
                        "Velo dispatches by name, keep one public method per name"
                    continue
                }
                val params = method.parameterTypes.mapIndexed { i, p ->
                    mapJvmType(p, method.genericParameterTypes.getOrNull(i), registry)
                        ?: VmType.Any.also {
                            problems += "Native method '$veloName.${method.name}' parameter #${i + 1} " +
                                "has unmappable type ${p.name}"
                        }
                }
                val returns = mapJvmType(method.returnType, method.genericReturnType, registry)
                    ?: VmType.Any.also {
                        problems += "Native method '$veloName.${method.name}' return type " +
                            "${method.returnType.name} is unmappable"
                    }
                methods[method.name] = NativeMethodDescriptor(
                    name = method.name,
                    params = params,
                    returns = returns,
                    jvmParams = method.parameterTypes.toList(),
                    handle = lookup.unreflect(method),
                )
            }

            if (problems.isNotEmpty()) {
                throw NativeMappingException(problems)
            }
            return NativeClassDescriptor(
                veloName = veloName,
                jvmClass = jvmClass,
                ctorParams = ctorParams,
                ctorJvmParams = ctor!!.parameterTypes.toList(),
                ctorHandle = lookup.unreflectConstructor(ctor),
                methods = methods,
            )
        }

        /**
         * Map a JVM type to its Velo-visible [VmType], or `null` when there
         * is no representation.
         *
         * Callbacks come in two host flavours:
         *   - [VeloFunction] — explicit handle with `post`/`call`; the Velo
         *     signature is unknown at the declaration (`args = null`), so
         *     call sites compile loosely and the runtime validates against
         *     the actual function's signature.
         *   - Kotlin function types (`(Int) -> Unit` ⇒ `Function1<Integer, Unit>`)
         *     — full signature recovered from generics, giving strict
         *     compile-time checking; the host receives a plain lambda.
         */
        fun mapJvmType(c: Class<*>, generic: JType?, registry: NativeRegistry): VmType? = when {
            c == java.lang.Void.TYPE || c == Unit::class.java -> VmType.Void
            c == Integer.TYPE || c == java.lang.Integer::class.java -> VmType.Int
            c == java.lang.Float.TYPE || c == java.lang.Float::class.java -> VmType.Float
            c == java.lang.Boolean.TYPE || c == java.lang.Boolean::class.java -> VmType.Bool
            c == java.lang.Byte.TYPE || c == java.lang.Byte::class.java -> VmType.Byte
            c == String::class.java -> VmType.Str
            c == Any::class.java -> VmType.Any
            c.isArray -> mapJvmType(c.componentType, null, registry)?.let { VmType.Array(it) }
            List::class.java.isAssignableFrom(c) -> VmType.Array(genericArg(generic, 0, registry))
            Map::class.java.isAssignableFrom(c) -> VmType.Dict(
                keyType = genericArg(generic, 0, registry),
                valueType = genericArg(generic, 1, registry),
            )
            c == VeloFunction::class.java -> VmType.Func(args = null, ret = VmType.Void)
            isKotlinFunction(c) -> mapKotlinFunction(generic, registry)
            else -> registry.getByJvmClass(c)?.let { VmType.Class(it.veloName) }
        }

        private fun genericArg(generic: JType?, index: Int, registry: NativeRegistry): VmType {
            val parameterized = generic as? ParameterizedType ?: return VmType.Any
            val arg = parameterized.actualTypeArguments.getOrNull(index) ?: return VmType.Any
            val raw = rawClass(arg) ?: return VmType.Any
            return mapJvmType(raw, null, registry) ?: VmType.Any
        }

        /**
         * Kotlin reflects declared function-type parameters with wildcards
         * (`Function1<? super Integer, Unit>`); unwrap them to the bound.
         */
        private fun rawClass(type: JType): Class<*>? = when (type) {
            is Class<*> -> type
            is java.lang.reflect.WildcardType ->
                (type.lowerBounds.firstOrNull() ?: type.upperBounds.firstOrNull())?.let { rawClass(it) }
            is ParameterizedType -> type.rawType as? Class<*>
            else -> null
        }

        private fun isKotlinFunction(c: Class<*>): Boolean =
            c.name.startsWith("kotlin.jvm.functions.Function")

        /**
         * `(Int, String) -> Unit` reflects as `Function2<Integer, String, Unit>`:
         * the last type argument is the return (must be `Unit` — callbacks
         * are void), the rest are the callback's argument types.
         */
        private fun mapKotlinFunction(generic: JType?, registry: NativeRegistry): VmType? {
            val parameterized = generic as? ParameterizedType ?: return null
            val typeArgs = parameterized.actualTypeArguments
            if (typeArgs.isEmpty()) return null
            val ret = rawClass(typeArgs.last()) ?: return null
            if (ret != Unit::class.java && ret != java.lang.Void::class.java) return null
            val args = typeArgs.dropLast(1).map { arg ->
                val raw = rawClass(arg) ?: return null
                mapJvmType(raw, null, registry) ?: return null
            }
            return VmType.Func(args = args, ret = VmType.Void)
        }
    }
}

/**
 * Registration/linking problem with a host class. Carries every issue found
 * so the host developer fixes the whole class in one pass.
 */
class NativeMappingException(val problems: List<String>) :
    RuntimeException(problems.joinToString(separator = "\n - ", prefix = "Native binding failed:\n - "))
