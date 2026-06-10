package vm.actors

import vm.Record
import vm.VMContext
import vm.records.EmptyRecord
import vm.records.FuncRecord
import vm.records.RefKind
import vm.records.RefRecord
import vm.records.ValueRecord

/**
 * Marshals [Record]s into thread-safe [ActorValue]s and back.
 *
 * Why this exists:
 *   - VM `Record`s, especially [RefRecord], reference state owned by a single
 *     [vm.MemoryArea]. Sending them across actor threads as-is would alias
 *     mutable state between two VM contexts and immediately race.
 *   - Native objects, raw frames, function values and pointers cannot be
 *     replicated safely (they are tied to JVM identity, native registry
 *     indices, captured `Vars`, or memory addresses respectively).
 *
 * Cloneable: primitives, host strings/booleans/chars/bytes/numbers, arrays
 * (which double as the runtime form of tuples), dicts, plus two handle
 * types that travel by reference instead of by copy: [ActorRefRecord]s and
 * function values ([FuncRecord]/[CallbackRecord] → [ActorValue.Callback],
 * pinned to the actor that owns the closure). Anything else is rejected
 * with a clear error so invalid attempts surface at the call site rather
 * than corrupting state.
 *
 * Note: this is a runtime safety net. The compiler statically rejects
 * non-transferable types in `actor class` signatures (see
 * [compiler.nodes.requireTransferable]), so well-typed programs never trip
 * the rejection paths here.
 */
object StructuredClone {

    /**
     * Convert a record from [source] into an [ActorValue] safe to hand to
     * another thread.
     *
     * Strings, booleans and other primitives wrap as [ActorValue.Primitive].
     * Containers recurse element-wise. Class instances and any non-marshallable
     * record types throw with an explanatory message.
     */
    fun encode(record: Record, source: VMContext): ActorValue {
        return when (record) {
            EmptyRecord -> ActorValue.Void
            is ValueRecord -> ActorValue.Primitive(record.get<Any>())
            is ActorRefRecord -> ActorValue.Ref(record.handle, record.objectId, record.className)
            // A function travels as a handle to (owner, closure). The owner of
            // a bare FuncRecord is whoever is encoding it — closures are only
            // ever created and held inside their own context. A CallbackRecord
            // is already a foreign handle; forwarding it preserves the
            // original owner (A → B → C still calls back into A).
            is FuncRecord -> {
                val owner = source.currentActor ?: throw ActorMarshallingException(
                    "Cannot transfer a function from a context without an actor identity."
                )
                ActorValue.Callback(owner, record)
            }
            is CallbackRecord -> ActorValue.Callback(record.handle, record.func)
            is RefRecord -> when (record.kind) {
                RefKind.ARRAY -> {
                    val array: Array<Record> = record.get(source)
                    ActorValue.Array(array.map { encode(it, source) })
                }
                RefKind.DICT -> {
                    val dict: MutableMap<Record, Record> = record.get(source)
                    ActorValue.Dict(dict.entries.map { (k, v) ->
                        encode(k, source) to encode(v, source)
                    })
                }
                RefKind.CLASS -> throw ActorMarshallingException(
                    "Cannot transfer non-actor class instance across actors; " +
                        "wrap the type as `actor[T]` to share by reference."
                )
                RefKind.NATIVE -> throw ActorMarshallingException(
                    "Cannot transfer native objects across actors."
                )
            }
            else -> throw ActorMarshallingException(
                "Cannot transfer ${record::class.simpleName} across actors."
            )
        }
    }

    /**
     * Materialise an [ActorValue] into a [Record] inside the receiver's
     * VM context. Containers allocate fresh entries in [target]'s memory area,
     * so the returned record is independent of any other actor's memory.
     */
    fun decode(value: ActorValue, target: VMContext): Record {
        return when (value) {
            ActorValue.Void -> EmptyRecord
            is ActorValue.Primitive -> ValueRecord(value.value)
            is ActorValue.Ref -> ActorRefRecord(value.handle, value.objectId, value.className)
            // A callback coming home (A → B → ... → A) unwraps to the
            // original closure: it is local again, no mailbox hop needed.
            is ActorValue.Callback ->
                if (value.handle === target.currentActor) value.func
                else CallbackRecord(value.handle, value.func)
            is ActorValue.Array -> {
                val arr: Array<Record> = Array(value.items.size) { idx -> decode(value.items[idx], target) }
                RefRecord.array(arr, target)
            }
            is ActorValue.Dict -> {
                val map = LinkedHashMap<Record, Record>(value.entries.size)
                for ((k, v) in value.entries) {
                    map[decode(k, target)] = decode(v, target)
                }
                RefRecord.dict(map, target)
            }
        }
    }
}

/**
 * Thrown by [StructuredClone] when a value isn't safe to ship between actor
 * domains. Caught by the actor-call opcodes and reported back to the caller as
 * an [ActorResponse.Failure] so user code sees a normal Velo error instead of
 * a host stack trace.
 */
class ActorMarshallingException(message: String) : RuntimeException(message)
