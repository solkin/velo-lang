package vm

import vm.records.FuncRecord
import vm.records.PtrRecord
import vm.records.RefKind
import vm.records.RefRecord
import java.util.Collections
import java.util.IdentityHashMap

/**
 * Precise per-actor mark-sweep over a [MemoryAreaImpl] handle-table heap.
 *
 * Driven by [VMExecutor] through [VMContext.collectIfNeeded] at op boundaries,
 * where the operand stacks hold every live value, so the root set is exact:
 *  - every [Frame] on the actor's call stack (operand stack + variable chain);
 *  - frames of fibers parked on an `await`, whose stacks are detached from the
 *    context (VEL-11);
 *  - the actor's registered root objects, which carry state between messages.
 *
 * Tracing follows only references that live in *this* heap — captured closure
 * scopes, array elements, class fields and pointer referents. Handles to other
 * actors (`actor[T]`, `future[T]`, foreign callbacks) are leaves: their state
 * belongs to another heap and must never be marked here.
 *
 * The walk is iterative over an explicit work list (Velo structures can nest
 * arbitrarily deep, so recursion would risk a stack overflow), and dedups
 * [Vars] scopes by identity so shared/captured scopes are visited once and
 * reference cycles terminate.
 */
internal object HeapCollector {

    fun collect(ctx: VMContext, extraRoots: List<Frame>) {
        val mem = ctx.memory
        mem.clearMarks()

        val work = ArrayDeque<Record>()
        val seenVars: MutableSet<Vars> = Collections.newSetFromMap(IdentityHashMap())

        ctx.stack.forEach { addFrame(it, work, seenVars) }
        for (frame in extraRoots) addFrame(frame, work, seenVars)

        while (work.isNotEmpty()) {
            val record = work.removeLast()
            when (record) {
                is RefRecord -> if (mem.mark(record.id)) {
                    when (record.kind) {
                        RefKind.ARRAY -> {
                            val array: Array<Record> = mem.get(record.id)
                            for (element in array) work.addLast(element)
                        }
                        RefKind.CLASS -> addFrame(mem.get(record.id), work, seenVars)
                        RefKind.NATIVE -> {} // opaque host object: no Velo children
                    }
                }
                is FuncRecord -> addVars(record.capturedVars, work, seenVars)
                is PtrRecord -> record.pointee()?.let { work.addLast(it) }
                else -> {} // ValueRecord, EmptyRecord, foreign actor/future/callback handles
            }
        }

        mem.sweep()
        mem.noteCollected()
    }

    private fun addFrame(frame: Frame, work: ArrayDeque<Record>, seenVars: MutableSet<Vars>) {
        frame.subs.forEach { work.addLast(it) }
        addVars(frame.vars, work, seenVars)
    }

    /** Enqueue every record in [vars] and its parent chain, each scope once. */
    private fun addVars(vars: Vars?, work: ArrayDeque<Record>, seenVars: MutableSet<Vars>) {
        var scope = vars
        while (scope != null && seenVars.add(scope)) {
            for (record in scope.vars.values) work.addLast(record)
            scope = scope.parent
        }
    }
}
