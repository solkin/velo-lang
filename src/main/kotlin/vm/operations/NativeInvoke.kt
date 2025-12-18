package vm.operations

import vm.Operation
import vm.Record
import vm.VMContext
import vm.VmType
import vm.records.RefRecord
import vm.records.ValueRecord
import java.lang.reflect.Method

class NativeInvoke(val args: List<Pair<Int, VmType>>) : Operation {

    override fun exec(pc: Int, ctx: VMContext): Int {
        val frame = ctx.currentFrame()

        val instance = frame.subs.pop().get<Any>()
        val method = frame.subs.pop().get<Method>()

        try {
            val nativeResult = method.invoke(instance, *args.map { arg ->
                val record = frame.vars.get(arg.first)
                record.getAs(vmType = arg.second, ctx = ctx)
            }.toTypedArray())

            nativeResult?.let {
                val result = wrapNativeResult(nativeResult, ctx)
                frame.subs.push(result)
            }
        } catch (ex: NoSuchMethodException) {
            throw Exception("Unable to invoke native method ${method.name}: ${ex.message}")
        }

        return pc + 1
    }

    /**
     * Wraps a native (JVM) result into a Velo Record.
     * Handles conversion of arrays and maps to Velo's internal format.
     */
    private fun wrapNativeResult(value: Any, ctx: VMContext): Record {
        return when (value) {
            // Primitives - wrap directly in ValueRecord
            is Boolean, is Byte, is Int, is Long, is Float, is Double, is Char, is String -> {
                ValueRecord(value)
            }
            // Arrays - convert to Array<Record> and store in memory
            is Array<*> -> {
                val veloArray = Array<Record>(value.size) { i ->
                    val element = value[i]
                    if (element != null) {
                        wrapNativeResult(element, ctx)
                    } else {
                        ValueRecord(Unit) // null placeholder
                    }
                }
                RefRecord.array(veloArray, ctx)
            }
            // Maps - convert to MutableMap<Record, Record>
            is Map<*, *> -> {
                val veloMap = HashMap<Record, Record>()
                for ((k, v) in value) {
                    if (k != null && v != null) {
                        veloMap[wrapNativeResult(k, ctx)] = wrapNativeResult(v, ctx)
                    }
                }
                RefRecord.dict(veloMap, ctx)
            }
            // Lists - convert to Array<Record>
            is List<*> -> {
                val veloArray = Array<Record>(value.size) { i ->
                    val element = value[i]
                    if (element != null) {
                        wrapNativeResult(element, ctx)
                    } else {
                        ValueRecord(Unit)
                    }
                }
                RefRecord.array(veloArray, ctx)
            }
            // Other objects - store in memory as native
            else -> {
                RefRecord.native(value, ctx)
            }
        }
    }

}
