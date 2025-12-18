package vm

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

    /** Get value as a mutable map (for Velo dictionaries) */
    fun getDict(): MutableMap<Record, Record> = get()

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
            is VmByte -> getByte()
            is VmAny -> get()
            is VmInt -> getInt()
            is VmFloat -> getFloat()
            is VmStr -> getString()
            is VmBool -> getBool()
            is VmTuple -> convertTuple(vmType, ctx)
            is VmArray -> convertArray(vmType, ctx)
            is VmDict -> convertDict(vmType, ctx)
            is VmClass -> {
                // For native classes, extract the underlying JVM object from the class frame
                if (this !is RefRecord || kind != RefKind.CLASS) {
                    throw IllegalStateException("Non-class reference record")
                }
                val index = nativeIndex ?: throw IllegalStateException("Native index is not defined")
                // Get the class frame from context
                val frame = if (ctx != null) get<Frame>(ctx) else getFrame()
                // Extract and cast the native instance from the frame's variables
                frame.vars.get(index).cast(vmType.toJvmType())
            }
            else -> throw IllegalArgumentException("Inconvertible type $vmType")
        }
    }

    /**
     * Convert a Velo array (Array<Record>) to a typed JVM array.
     */
    private fun convertArray(vmType: VmArray, ctx: VMContext?): Any {
        val veloArray = getArray()
        val elementType = vmType.elementType
        // Use boxed types for array elements (e.g., Integer instead of int)
        val elementClass = elementType.toBoxedJvmType()
        
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
    private fun convertTuple(vmType: VmTuple, ctx: VMContext?): Any {
        val veloArray = getArray()
        
        // If no element types specified, return as Object[]
        if (vmType.elementTypes.isEmpty()) {
            return veloArray.map { it.get<Any>() }.toTypedArray()
        }
        
        // Convert each element according to its type
        return veloArray.mapIndexed { i, record ->
            val elemType = vmType.elementTypes.getOrElse(i) { VmAny() }
            record.getAs(elemType, ctx)
        }.toTypedArray()
    }

    /**
     * Convert a Velo dict (MutableMap<Record, Record>) to a typed JVM Map.
     */
    private fun convertDict(vmType: VmDict, ctx: VMContext?): Any {
        val veloDict = getDict()
        val keyType = vmType.keyType
        val valueType = vmType.valueType
        
        val jvmMap = LinkedHashMap<Any, Any>()
        
        for ((k, v) in veloDict) {
            val jvmKey = k.getAs(keyType, ctx)
            val jvmValue = v.getAs(valueType, ctx)
            jvmMap[jvmKey] = jvmValue
        }
        
        return jvmMap
    }

}
