package vm3

/**
 * One active `try` (VEL-9): where to resume on a caught error ([catchPc]) plus
 * the marks to restore the owning activation while unwinding — its lexical
 * environment ([savedEnv], undoing any open `Op.ScopeEnter`) and its base in the
 * fiber's tagged operand stack ([savedSp], dropping half-evaluated operands).
 */
internal class Handler(
    val catchPc: Int,
    val savedEnv: Env,
    val savedSp: Int,
)

/**
 * Carrier for a Velo `throw`: ferries the raised `Error` instance up to the
 * nearest active `try` handler. Distinct from the JVM exceptions a failing op
 * raises (turned into an `Error` at the catch site) — a `VeloThrow` already
 * carries the user's `Error`. The [message] renders "kind: message" so an
 * uncaught throw (and an actor failure crossing `await`) reads well.
 */
internal class VeloThrow(val error: Any?, override val message: String?) : RuntimeException(message)
