package vm.records

import vm.Frame
import vm.MemoryArea
import vm.Record
import vm.VMContext

/**
 * Kind of reference stored in RefRecord.
 */
enum class RefKind {
    /** Array of Records */
    ARRAY,
    /** Dictionary (MutableMap<Record, Record>) */
    DICT,
    /** Class instance (Frame) */
    CLASS,
    /** Native JVM object */
    NATIVE
}

/**
 * Unified record for all reference types stored in the memory area.
 * Replaces LinkRecord, ClassRecord, and NativeRecord.
 *
 * @param id The ID in the memory area
 * @param kind The kind of reference
 * @param nativeIndex For CLASS kind, the index where native instance is stored (if any)
 * @param memory Transient reference to memory area for direct access
 */
data class RefRecord(
    val id: Int,
    val kind: RefKind,
    val nativeIndex: Int? = null,
    @Transient private val memory: MemoryArea? = null
) : Record {

    @Suppress("UNCHECKED_CAST")
    override fun <T> get(): T {
        val m = memory ?: throw IllegalStateException("RefRecord requires memory context for get()")
        return m.get(id)
    }

    fun <T> get(ctx: VMContext): T = ctx.memoryGet(id)

    /**
     * Get value as an array of Records.
     * Validates that this is an ARRAY reference.
     */
    override fun getArray(): Array<Record> {
        if (kind != RefKind.ARRAY) {
            throw IllegalStateException("Expected ARRAY reference, got $kind")
        }
        return get()
    }

    /**
     * Get value as a mutable map.
     * Validates that this is a DICT reference.
     */
    override fun getDict(): MutableMap<Record, Record> {
        if (kind != RefKind.DICT) {
            throw IllegalStateException("Expected DICT reference, got $kind")
        }
        return get()
    }

    /**
     * Get value as a Frame.
     * Validates that this is a CLASS reference.
     */
    override fun getFrame(): Frame {
        if (kind != RefKind.CLASS) {
            throw IllegalStateException("Expected CLASS reference, got $kind")
        }
        return get()
    }

    companion object {
        /**
         * Create a RefRecord for an array.
         */
        fun array(value: Array<Record>, ctx: VMContext): RefRecord {
            return RefRecord(
                id = ctx.memoryPut(value),
                kind = RefKind.ARRAY,
                memory = ctx.memory
            )
        }

        /**
         * Create a RefRecord for a dictionary.
         */
        fun dict(value: MutableMap<Record, Record>, ctx: VMContext): RefRecord {
            return RefRecord(
                id = ctx.memoryPut(value),
                kind = RefKind.DICT,
                memory = ctx.memory
            )
        }

        /**
         * Create a RefRecord for a class instance.
         */
        fun classInstance(frame: Frame, nativeIndex: Int?, ctx: VMContext): RefRecord {
            return RefRecord(
                id = ctx.memoryPut(frame),
                kind = RefKind.CLASS,
                nativeIndex = nativeIndex,
                memory = ctx.memory
            )
        }

        /**
         * Create a RefRecord for a native JVM object.
         */
        fun native(value: Any, ctx: VMContext): RefRecord {
            return RefRecord(
                id = ctx.memoryPut(value),
                kind = RefKind.NATIVE,
                memory = ctx.memory
            )
        }
    }
}

