package vm

import kotlin.reflect.KClass

/**
 * Information about a registered native class
 */
data class NativeClassInfo(
    val veloName: String,
    val jvmClass: Class<*>,
)

/**
 * Registry for native classes that can be used from Velo Lang.
 * Allows mapping between Velo class names and JVM classes.
 *
 * Registration itself is just a name↔class mapping. The full binding —
 * introspection, signature mapping, [java.lang.invoke.MethodHandle]
 * resolution — happens lazily in [descriptor] on first compile/link use and
 * is cached. Laziness makes registration order irrelevant for classes that
 * reference each other in their signatures.
 */
class NativeRegistry {
    private val byVeloName = HashMap<String, NativeClassInfo>()
    private val byJvmClass = HashMap<Class<*>, NativeClassInfo>()
    private val descriptors = HashMap<String, NativeClassDescriptor>()

    /**
     * Register a native class with the same name in Velo and JVM
     */
    fun register(jvmClass: Class<*>): NativeRegistry {
        return register(jvmClass.simpleName, jvmClass)
    }

    /**
     * Register a native class with Kotlin KClass
     */
    fun register(jvmClass: KClass<*>): NativeRegistry {
        return register(jvmClass.java)
    }

    /**
     * Register a native class with a custom Velo name
     */
    fun register(veloName: String, jvmClass: Class<*>): NativeRegistry {
        val info = NativeClassInfo(veloName, jvmClass)
        byVeloName[veloName] = info
        byJvmClass[jvmClass] = info
        return this
    }

    /**
     * Register a native class with a custom Velo name using Kotlin KClass
     */
    fun register(veloName: String, jvmClass: KClass<*>): NativeRegistry {
        return register(veloName, jvmClass.java)
    }

    /**
     * Get native class info by Velo name
     */
    fun getByVeloName(name: String): NativeClassInfo? = byVeloName[name]

    /**
     * Get native class info by JVM class
     */
    fun getByJvmClass(clazz: Class<*>): NativeClassInfo? = byJvmClass[clazz]

    /**
     * Check if a Velo class name is registered
     */
    fun isRegistered(veloName: String): Boolean = byVeloName.containsKey(veloName)

    /**
     * Check if a JVM class is registered
     */
    fun isRegistered(jvmClass: Class<*>): Boolean = byJvmClass.containsKey(jvmClass)

    /**
     * Get all registered class names
     */
    fun getAllNames(): Set<String> = byVeloName.keys.toSet()

    /**
     * Get (building and caching on first use) the full binding descriptor
     * for a registered class, or `null` when the name is not registered.
     *
     * @throws NativeMappingException when the host class cannot be bound
     *   (overloads, multiple constructors, unmappable signature types) —
     *   the message lists every problem at once.
     */
    fun descriptor(veloName: String): NativeClassDescriptor? {
        descriptors[veloName]?.let { return it }
        val info = byVeloName[veloName] ?: return null
        val descriptor = NativeClassDescriptor.introspect(info.veloName, info.jvmClass, registry = this)
        descriptors[info.veloName] = descriptor
        return descriptor
    }
}

