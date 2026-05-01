package vm.records

import vm.Record
import vm.Vars

/**
 * Record representing a Velo function value.
 *
 * Carries:
 *  - [frameNum]: the bytecode frame number to invoke when called.
 *  - [capturedVars]: the variable chain that was current at the moment
 *    the function value was created. This gives lexical (definition-site)
 *    scoping for closures: captured outer variables remain accessible
 *    when the function is invoked from any other location.
 *
 * Lifetime is managed by ordinary JVM GC. As long as a [FuncRecord] is
 * reachable (variable, operand stack, mailbox, JVM listener via proxy),
 * the captured [Vars] chain — and any [Record]s it transitively
 * references — stay alive. No entries are placed in [vm.MemoryArea].
 */
data class FuncRecord(
    val frameNum: Int,
    val capturedVars: Vars,
) : Record {

    @Suppress("UNCHECKED_CAST")
    override fun <T> get(): T = frameNum as T

    override fun getInt(): Int = frameNum
}
