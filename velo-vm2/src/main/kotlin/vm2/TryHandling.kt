package vm2

/**
 * One active `try` (VEL-9): where to resume on a caught error ([catchIp]) plus
 * the marks to restore the owning frame while unwinding — its lexical scope
 * ([savedScope], undoing any open `Op.ScopeEnter`) and its base in the fiber's
 * shared value stack ([savedTop], dropping half-evaluated operands).
 */
class Handler(
    val catchIp: Int,
    val savedScope: Frame,
    val savedTop: Int,
)

/**
 * Carrier for a Velo `throw`: ferries the raised `Error` instance up to the
 * nearest active `try` handler. Distinct from the JVM exceptions a failing op
 * raises (turned into an `Error` at the catch site) — a `VeloThrow` already
 * carries the user's `Error`. The [message] renders "kind: message" so an
 * uncaught throw and an actor failure crossing `await` read well.
 */
class VeloThrow(val error: Any?, override val message: String?) : RuntimeException(message)
