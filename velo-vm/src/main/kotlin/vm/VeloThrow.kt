package vm

/**
 * The carrier for a Velo `throw`: it ferries the raised [Error] record up
 * through the interpreter loop to the nearest active `try` handler, where
 * [VMExecutor] unwinds it back into a Velo value. Distinct from the JVM
 * exceptions a failing op raises (arithmetic, null, native, …): those are
 * turned into an `Error` at the catch site, whereas a `VeloThrow` already
 * carries the user's own `Error` record. Unlike [HaltException], it is
 * catchable.
 */
class VeloThrow(val error: Record, override val message: String?) : RuntimeException(message)
