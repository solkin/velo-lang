package core

import java.util.concurrent.CompletableFuture

/**
 * A Velo function value handed to native (JVM) code — the host side of a
 * callback. Declare a native method parameter as `VeloFunction` (or as a
 * Kotlin function type up to 4 arguments) to receive one.
 *
 * The host may keep the handle for as long as it wants and invoke it from
 * **any thread**: invocation only encodes the arguments and posts them to
 * the dispatcher of the actor that owns the closure, so the body always
 * runs on the owner's thread (for the main context that is the host's
 * chosen main/UI dispatcher).
 *
 * Supported argument types: Int, Float, Boolean, Byte, String, List, Map,
 * and other VeloFunction handles. Arguments are validated against the
 * function's declared Velo signature when it is known.
 *
 * Lifetime: each handle pins the owning actor, keeping its dispatcher
 * serviceable — including the main pump loop in CLI mode — until the host
 * drops the reference.
 */
interface VeloFunction {

    /**
     * Invoke the callback on its owner's thread, without waiting — the
     * normal mode for event callbacks. Failures inside the callback are
     * program-fatal, consistent with every other unhandled Velo runtime
     * error.
     */
    fun post(vararg args: Any?)

    /**
     * Invoke the callback on its owner's thread and observe completion.
     * Velo callbacks return void, so the future resolves to `null` on
     * success; failures complete it exceptionally. When called from the
     * owner's own thread (a native invoked synchronously from Velo code),
     * the callback executes inline to avoid self-deadlock; otherwise never
     * block the owner's thread on the returned future from itself.
     */
    fun call(vararg args: Any?): CompletableFuture<Any?>
}
