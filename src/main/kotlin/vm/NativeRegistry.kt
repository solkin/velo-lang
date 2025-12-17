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
 */
class NativeRegistry {
    private val byVeloName = HashMap<String, NativeClassInfo>()
    private val byJvmClass = HashMap<Class<*>, NativeClassInfo>()

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
}

