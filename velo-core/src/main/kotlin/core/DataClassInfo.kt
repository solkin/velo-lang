package core

/**
 * Marshalling metadata for a `data class`, keyed by its bytecode frame number.
 *
 * A data class is an immutable value type: it is copied (not aliased) across
 * actor and native boundaries. To copy an instance the runtime needs to know
 * which of the instance frame's variables are the fields (and in what order),
 * which is exactly what [fields] records. The class is rebuilt on the far side
 * by re-running its constructor with the decoded field values — safe because a
 * data class body may only declare methods, so construction has no side
 * effects.
 *
 * Field names and types are carried for the native bridge (structural matching
 * against a registered JVM type).
 */
data class DataClassInfo(
    val frameNum: Int,
    val name: String,
    val fields: List<DataField>,
)

data class DataField(
    val name: String,
    val index: Int,
    val type: VmType,
)
