package vm

import vm.records.EmptyRecord

/**
 * Rebuild a `data class` instance in [target] by re-running its constructor
 * with the decoded [fields] as arguments. The class frame is run to
 * completion under a zero-op sentinel (the mechanism
 * [vm.actors.ActorHandle] uses for method dispatch), leaving the fresh
 * instance on the sentinel's operand stack.
 *
 * Shared by both marshalling boundaries — actor↔actor ([vm.actors.StructuredClone])
 * and velo↔native ([NativeBridge]). Safe to re-run because a data class body
 * declares only methods, so construction has no observable side effects.
 */
internal fun reconstructData(classFrameNum: Int, fields: List<Record>, target: VMContext): Record {
    val classFrame = target.loadFrame(classFrameNum, parentVars = null)
        ?: throw IllegalStateException("Data class frame $classFrameNum not found")
    val sentinel = Frame(pc = 0, subs = LifoStack(), vars = createVars(emptyList(), null), ops = emptyList())
    // Reconstruction is a synchronous, pure sub-computation that may run nested
    // inside another op (e.g. decoding a `FutureAwait` response). Disable
    // suspension (VEL-11) for its duration: a stray park here would leave the
    // outer fiber's frames on the stack and corrupt its resume.
    val previousSuspension = target.suspensionEnabled
    target.suspensionEnabled = false
    try {
        target.pushFrame(sentinel)
        fields.forEach { classFrame.subs.push(it) }
        target.pushFrame(classFrame)
        VMExecutor(target).run()
        val tail = target.popFrame()
        check(tail === sentinel) { "Data class reconstruction: stack out of sync" }
        return if (tail.subs.empty()) EmptyRecord else tail.subs.pop()
    } finally {
        target.suspensionEnabled = previousSuspension
    }
}
