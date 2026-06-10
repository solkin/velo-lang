package vm

import core.VmType
import vm.records.RefRecord
import vm.records.RefKind
import java.lang.reflect.Array as JArray

/**
 * Record is the base interface for all values stored in the VM.
 * 
 * Records represent different types of data in the Velo VM:
 * - ValueRecord: primitive values (int, float, string, bool, etc.)
 * - RefRecord: references to objects stored in the MemoryArea (arrays, dicts, classes, native)
 * - PtrRecord and variants: pointer types for memory manipulation
 * - EmptyRecord: singleton for uninitialized variables
 */
interface Record {

    /**
     * Get the underlying value of this record.
     * The actual type depends on the record implementation.
     */
    fun <T> get(): T

    /**
     * Cast the underlying value to a specific JVM class type.
     * 
     * @param targetClass The target class to cast to
     * @return The value cast to the target type
     * @throws ClassCastException if the value cannot be cast
     */
    fun <T> cast(targetClass: Class<T>): T {
        return targetClass.cast(get())
    }

    // Type-specific getters for convenience
    fun getBool(): Boolean = get()
    fun getNumber(): Number = get()
    fun getByte(): Byte = get()
    fun getInt(): Int = get()
    fun getLong(): Long = get()
    fun getFloat(): Float = get()
    fun getDouble(): Double = get()
    fun getChar(): Char = get()
    fun getString(): String = get()

    /** Get value as an array of Records (for Velo arrays and tuples) */
    fun getArray(): Array<Record> = get()

    /** Get value as a Frame (for class instances) */
    fun getFrame(): Frame = get()

    /**
     * Convert this record's value to a JVM type suitable for native method calls.
     * 
     * This method is used when passing Velo values to native (JVM) methods.
     * It handles the conversion from Velo's internal representation to JVM types.
     * 
     * @param vmType The Velo VM type describing the expected conversion
     * @param ctx Optional VM context, required for VmClass conversions to access memory area
     * @return The converted JVM value ready for use in native method invocation
     * @throws IllegalStateException if conversion requires context but none provided
     * @throws IllegalArgumentException if the type cannot be converted
     */
    fun getAs(vmType: VmType, ctx: VMContext? = null): Any {
        return when (vmType) {
            is VmType.Byte -> getByte()
            is VmType.Any -> get()
            is VmType.Int -> getInt()
            is VmType.Float -> getFloat()
            is VmType.Str -> getString()
            is VmType.Bool -> getBool()
            is VmType.Tuple -> convertTuple(vmType, ctx)
            is VmType.Array -> convertArray(vmType, ctx)
            is VmType.Func -> convertFunc(vmType, ctx)
            is VmType.Class -> {
                // Native instances are opaque handles — unwrap the JVM object.
                if (this !is RefRecord || kind != RefKind.NATIVE) {
                    throw IllegalStateException("Expected a native ${vmType.name} instance, got ${this::class.simpleName}")
                }
                val context = ctx ?: throw IllegalStateException("Native instance access requires a VM context")
                get<Any>(context)
            }
            else -> throw IllegalArgumentException("Inconvertible type $vmType")
        }
    }

    /**
     * Wrap a Velo function value into a [core.VeloFunction] handle for a
     * native method parameter. The wrapper pins the actor that owns the
     * closure and routes every host invocation through that actor's
     * dispatcher, so the body always runs on its owner's thread.
     */
    private fun convertFunc(vmType: VmType.Func, ctx: VMContext?): Any {
        val actor = ctx?.currentActor
            ?: throw IllegalStateException("Passing a callback to native code requires an actor context")
        return when (this) {
            is vm.records.FuncRecord -> vm.actors.VeloFunctionImpl(actor, this, vmType.args)
            is vm.actors.CallbackRecord -> vm.actors.VeloFunctionImpl(handle, func, vmType.args)
            else -> throw IllegalArgumentException(
                "Expected a function value for a func parameter, got ${this::class.simpleName}"
            )
        }
    }

    /**
     * Convert a Velo array (Array<Record>) to a typed JVM array.
     */
    private fun convertArray(vmType: VmType.Array, ctx: VMContext?): Any {
        val veloArray = getArray()
        val elementType = vmType.elementType
        // Use boxed types for array elements (e.g., Integer instead of int)
        val elementClass = elementType.toBoxedJvm()
        
        // Create typed JVM array
        @Suppress("UNCHECKED_CAST")
        val jvmArray = JArray.newInstance(elementClass, veloArray.size)
        
        for (i in veloArray.indices) {
            val element = veloArray[i].getAs(elementType, ctx)
            JArray.set(jvmArray, i, element)
        }
        
        return jvmArray
    }

    /**
     * Convert a Velo tuple (Array<Record>) to a JVM array.
     */
    private fun convertTuple(vmType: VmType.Tuple, ctx: VMContext?): Any {
        val veloArray = getArray()
        
        // If no element types specified, return as Object[]
        if (vmType.elementTypes.isEmpty()) {
            return veloArray.map { it.get<Any>() }.toTypedArray()
        }
        
        // Convert each element according to its type
        return veloArray.mapIndexed { i, record ->
            val elemType = vmType.elementTypes.getOrElse(i) { VmType.Any }
            record.getAs(elemType, ctx)
        }.toTypedArray()
    }

}
