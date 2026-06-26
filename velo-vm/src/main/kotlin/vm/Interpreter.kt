package vm

import core.NullPtr
import core.Op
import core.VmType
import vm.actors.ActorHandle
import vm.actors.ActorRefRecord
import vm.actors.CallbackRecord
import vm.actors.FutureRecord
import vm.actors.StructuredClone
import vm.actors.popAndEncodeArgs
import vm.records.EmptyRecord
import vm.records.FuncRecord
import vm.records.PtrRecord
import vm.records.RefKind
import vm.records.RefRecord
import vm.records.ValueRecord
import kotlin.math.abs

/**
 * The instruction interpreter — the single place where bytecode executes.
 *
 * Each [Op] is inert data (see [core.Op] for the instruction set and the
 * stack effect of every op); [exec] maps one onto the running [VMContext]
 * and returns the program counter to run next. [VMExecutor] drives this on
 * the main thread and every actor worker reuses the same loop, so operation
 * semantics are identical inside and outside actors.
 *
 * The `when` is exhaustive over the sealed [Op] — adding an instruction
 * without an interpreter branch is a compile error. Branches are ordered
 * hot-first (variable access, literals, control flow, arithmetic) since the
 * dispatch is a sequential type test.
 */
object Interpreter {

    fun exec(op: Op, pc: Int, ctx: VMContext): Int = when (op) {

        // ---- Hot path: variables, literals, control flow, calls ----

        is Op.Load -> {
            val frame = ctx.currentFrame()
            frame.subs.push(frame.vars.get(op.index))
            pc + 1
        }

        is Op.Store -> {
            val frame = ctx.currentFrame()
            frame.vars.set(op.index, frame.subs.pop())
            pc + 1
        }

        is Op.Push -> {
            // NullPtr is the serializable null-pointer literal; materialise
            // the VM's record for it so pointer ops and equality see one value.
            val value = if (op.value === NullPtr) PtrRecord.Null else op.value
            ctx.currentFrame().subs.push(ValueRecord(value))
            pc + 1
        }

        is Op.If -> {
            val flag = ctx.currentFrame().subs.pop().getBool()
            pc + 1 + if (flag) 0 else op.elseSkip
        }

        is Op.Move -> pc + op.count + 1

        /*
         * The callable on top of the stack is a FuncRecord (plain function),
         * a CallbackRecord (function owned by another actor), or — legacy
         * bytecode — a bare frame number. A FuncRecord brings its captured
         * definition-site scope; `classParent` instead pops the receiver
         * instance and uses its variables (method dispatch). A foreign
         * CallbackRecord never grows the local call stack: its arguments are
         * structurally cloned and posted to the owner's dispatcher,
         * fire-and-forget (void by construction), so awaited-actor →
         * callback-into-awaiter cycles cannot deadlock. A callback invoked
         * by its own actor runs inline as a plain local call.
         */
        is Op.Call -> {
            val thisFrame = ctx.currentFrame()
            // Resume of a value-returning foreign callback that parked below
            // (cooperative path): the marker future left on the operand stack
            // rode the suspension and is now complete, so this op re-runs and
            // simply decodes the owner's reply. The peek is guarded by
            // `callbackResult` so ordinary calls don't pay for it.
            if (op.callbackResult && thisFrame.subs.peek() is FutureRecord) {
                val marker = thisFrame.subs.pop() as FutureRecord
                val response = marker.handle.unwrapResponse(marker.future.join())
                thisFrame.subs.push(StructuredClone.decode(response, ctx))
                pc + 1
            } else {
                val callable = thisFrame.subs.pop()
                if (callable is CallbackRecord && ctx.currentActor !== callable.handle) {
                    val argsArray = Array(size = abs(op.args), init = { thisFrame.subs.pop() })
                        .let { arr -> if (op.args > 0) arr.reversedArray() else arr }
                    val encoded = argsArray.map { StructuredClone.encode(it, ctx) }
                    if (op.callbackResult) {
                        // Value-returning callback. Post to the owner and, rather
                        // than blocking on the reply (which would deadlock the
                        // owner when both share one cooperative loop), park: the
                        // marker future rides the operand stack and this op
                        // re-runs above once the result is ready. Nested/inline
                        // calls (suspension off) still block — their JVM frames
                        // cannot be parked.
                        val future = callable.handle.requestInvokeAsync(callable.func, encoded)
                        if (!future.isDone && ctx.suspensionEnabled) {
                            thisFrame.subs.push(FutureRecord(callable.handle, future))
                            ctx.requestSuspend(future)
                            pc
                        } else {
                            val response = callable.handle.unwrapResponse(future.join())
                            thisFrame.subs.push(StructuredClone.decode(response, ctx))
                            pc + 1
                        }
                    } else {
                        // Void callback: fire-and-forget notification.
                        callable.handle.postInvoke(callable.func, encoded)
                        pc + 1
                    }
                } else {
                    val frameNum: Int
                    val capturedVars: Vars?
                    when (callable) {
                        is FuncRecord -> {
                            frameNum = callable.frameNum
                            capturedVars = callable.capturedVars
                        }
                        is CallbackRecord -> {
                            frameNum = callable.func.frameNum
                            capturedVars = callable.func.capturedVars
                        }
                        else -> {
                            frameNum = callable.getInt()
                            capturedVars = null
                        }
                    }
                    val argsArray = Array(size = abs(op.args), init = { thisFrame.subs.pop() })
                        .let { arr -> if (op.args > 0) arr.reversedArray() else arr }
                    val parentVars: Vars? = if (op.classParent) {
                        thisFrame.subs.pop().getFrame().vars
                    } else {
                        capturedVars ?: thisFrame.vars
                    }
                    val newFrame = ctx.loadFrame(num = frameNum, parentVars = parentVars)
                        ?: throw Exception("Frame $frameNum not found")
                    argsArray.forEach { arg -> newFrame.subs.push(arg) }
                    ctx.pushFrame(newFrame)
                    pc + 1
                }
            }
        }

        is Op.Ret -> {
            val frame = ctx.popFrame()
            if (!frame.subs.empty()) {
                ctx.currentFrame().subs.push(frame.subs.pop())
            }
            // The returned pc belongs to the dropped frame; the VM won't use it.
            pc
        }

        // ---- Arithmetic ----

        is Op.Add -> {
            val frame = ctx.currentFrame()
            val rec1 = frame.subs.pop().getNumber()
            val rec2 = frame.subs.pop().getNumber()
            frame.subs.push(ValueRecord(rec2.plus(rec1)))
            pc + 1
        }

        is Op.Sub -> {
            val frame = ctx.currentFrame()
            val rec1 = frame.subs.pop().getNumber()
            val rec2 = frame.subs.pop().getNumber()
            frame.subs.push(ValueRecord(rec2.minus(rec1)))
            pc + 1
        }

        is Op.Mul -> {
            val frame = ctx.currentFrame()
            val rec1 = frame.subs.pop().getNumber()
            val rec2 = frame.subs.pop().getNumber()
            frame.subs.push(ValueRecord(rec2.multiply(rec1)))
            pc + 1
        }

        is Op.Div -> {
            val frame = ctx.currentFrame()
            val rec1 = frame.subs.pop().getNumber()
            val rec2 = frame.subs.pop().getNumber()
            frame.subs.push(ValueRecord(rec2.divide(rec1)))
            pc + 1
        }

        is Op.Rem -> {
            val frame = ctx.currentFrame()
            val rec1 = frame.subs.pop().getInt()
            val rec2 = frame.subs.pop().getInt()
            frame.subs.push(ValueRecord(rec2 % rec1))
            pc + 1
        }

        // ---- Comparison ----

        is Op.More -> {
            val frame = ctx.currentFrame()
            val val1 = frame.subs.pop().getInt()
            val val2 = frame.subs.pop().getInt()
            frame.subs.push(ValueRecord(val2 > val1))
            pc + 1
        }

        is Op.Equals -> {
            val frame = ctx.currentFrame()
            val val1 = frame.subs.pop()
            val val2 = frame.subs.pop()
            // `data class` instances compare by value (field-by-field, deep);
            // every other value keeps its existing identity/primitive equality.
            val equal = if (isDataInstance(val1, ctx) && isDataInstance(val2, ctx)) {
                deepEquals(val1, val2, ctx)
            } else {
                val1 == val2
            }
            frame.subs.push(ValueRecord(equal))
            pc + 1
        }

        // ---- Stack manipulation ----

        is Op.Pop -> {
            ctx.currentFrame().subs.pop()
            pc + 1
        }

        is Op.Dup -> {
            val frame = ctx.currentFrame()
            frame.subs.push(frame.subs.peek())
            pc + 1
        }

        is Op.Swap -> {
            with(ctx.currentFrame().subs) {
                val rec1 = pop()
                val rec2 = pop()
                push(rec1)
                push(rec2)
            }
            pc + 1
        }

        is Op.Rot -> {
            with(ctx.currentFrame().subs) {
                val rec1 = pop()
                val rec2 = pop()
                val rec3 = pop()
                push(rec2)
                push(rec1)
                push(rec3)
            }
            pc + 1
        }

        // ---- Functions / classes ----

        is Op.Frame -> {
            val current = ctx.currentFrame()
            current.subs.push(FuncRecord(frameNum = op.num, capturedVars = current.vars))
            pc + 1
        }

        is Op.Instance -> {
            val frame = ctx.currentFrame()
            frame.subs.push(RefRecord.classInstance(frame, nativeIndex = null, ctx))
            pc + 1
        }

        is Op.Halt -> throw HaltException()

        // ---- Bitwise ----

        is Op.And -> {
            val frame = ctx.currentFrame()
            val val1 = frame.subs.pop().getInt()
            val val2 = frame.subs.pop().getInt()
            frame.subs.push(ValueRecord(val1.and(val2)))
            pc + 1
        }

        is Op.Or -> {
            val frame = ctx.currentFrame()
            val val1 = frame.subs.pop().getInt()
            val val2 = frame.subs.pop().getInt()
            frame.subs.push(ValueRecord(val1.or(val2)))
            pc + 1
        }

        is Op.Xor -> {
            val frame = ctx.currentFrame()
            val val1 = frame.subs.pop().getInt()
            val val2 = frame.subs.pop().getInt()
            frame.subs.push(ValueRecord(val1.xor(val2)))
            pc + 1
        }

        is Op.Shl -> {
            val frame = ctx.currentFrame()
            val bits = frame.subs.pop().getInt()
            val value = frame.subs.pop().getInt()
            frame.subs.push(ValueRecord(value.shl(bits)))
            pc + 1
        }

        is Op.Shr -> {
            val frame = ctx.currentFrame()
            val bits = frame.subs.pop().getInt()
            val value = frame.subs.pop().getInt()
            frame.subs.push(ValueRecord(value.shr(bits)))
            pc + 1
        }

        // ---- Conversions / hashing ----

        is Op.IntChar -> {
            val frame = ctx.currentFrame()
            val v = frame.subs.pop().getInt()
            frame.subs.push(ValueRecord(Char(v).toString()))
            pc + 1
        }

        is Op.IntStr -> {
            val frame = ctx.currentFrame()
            val v = frame.subs.pop().getInt()
            frame.subs.push(ValueRecord(v.toString()))
            pc + 1
        }

        is Op.StrInt -> {
            val frame = ctx.currentFrame()
            val str = frame.subs.pop().getString()
            frame.subs.push(ValueRecord(str.toInt()))
            pc + 1
        }

        is Op.Hash -> {
            val frame = ctx.currentFrame()
            frame.subs.push(ValueRecord(frame.subs.pop().hashCode()))
            pc + 1
        }

        // ---- Strings ----

        is Op.StrCon -> {
            val frame = ctx.currentFrame()
            val str1 = frame.subs.pop().getString()
            val str2 = frame.subs.pop().getString()
            frame.subs.push(ValueRecord(str2 + str1))
            pc + 1
        }

        is Op.StrLen -> {
            val frame = ctx.currentFrame()
            frame.subs.push(ValueRecord(frame.subs.pop().getString().length))
            pc + 1
        }

        is Op.StrIndex -> {
            val frame = ctx.currentFrame()
            val index = frame.subs.pop().getInt()
            val str = frame.subs.pop().getString()
            frame.subs.push(ValueRecord(str[index].code))
            pc + 1
        }

        is Op.StrSub -> {
            val frame = ctx.currentFrame()
            val start = frame.subs.pop().getInt()
            val end = frame.subs.pop().getInt()
            val str = frame.subs.pop().getString()
            frame.subs.push(ValueRecord(str.substring(start, end)))
            pc + 1
        }

        // ---- Arrays ----

        is Op.ArrNew -> {
            val frame = ctx.currentFrame()
            val size = frame.subs.pop().getInt()
            val array = Array<Record>(size) { EmptyRecord }
            frame.subs.push(RefRecord.array(array, ctx))
            pc + 1
        }

        is Op.ArrLen -> {
            val frame = ctx.currentFrame()
            frame.subs.push(ValueRecord(frame.subs.pop().getArray().size))
            pc + 1
        }

        is Op.ArrLoad -> {
            val frame = ctx.currentFrame()
            val count = frame.subs.pop().getInt()
            val index = frame.subs.pop().getInt()
            val array = frame.subs.pop().getArray()
            for (i in 0 until count) {
                frame.subs.push(value = array[index + i])
            }
            pc + 1
        }

        is Op.ArrStore -> {
            val frame = ctx.currentFrame()
            val count = frame.subs.pop().getInt()
            val index = frame.subs.pop().getInt()
            val arrayRec = frame.subs.pop()
            val array = arrayRec.getArray()
            var i = index + count - 1
            repeat(count) {
                array[i--] = frame.subs.pop()
            }
            // Push back the same reference (array is mutated in place)
            frame.subs.push(value = arrayRec)
            pc + 1
        }

        is Op.ArrCopy -> {
            val frame = ctx.currentFrame()
            val srcPos = frame.subs.pop().getInt()
            val dstPos = frame.subs.pop().getInt()
            val length = frame.subs.pop().getInt()
            val src = frame.subs.pop().getArray()
            val dst = frame.subs.pop().getArray()
            System.arraycopy(src, srcPos, dst, dstPos, length)
            pc + 1
        }

        // ---- Pointers ----

        is Op.PtrNew -> {
            val frame = ctx.currentFrame()
            frame.subs.push(PtrRecord.Box(frame.subs.pop()))
            pc + 1
        }

        is Op.PtrLoad -> {
            val frame = ctx.currentFrame()
            val ptr = frame.subs.pop()
            if (ptr !is PtrRecord) {
                throw IllegalStateException("PtrLoad requires a pointer on stack, got: ${ptr.javaClass.simpleName}")
            }
            if (ptr.isNull()) {
                throw NullPointerException("Null pointer dereference")
            }
            frame.subs.push(ptr.deref())
            pc + 1
        }

        is Op.PtrStore -> {
            val frame = ctx.currentFrame()
            val ptr = frame.subs.pop()
            val value = frame.subs.pop()
            if (ptr !is PtrRecord) {
                throw IllegalStateException("PtrStore requires a pointer on stack, got: ${ptr.javaClass.simpleName}")
            }
            if (ptr.isNull()) {
                throw NullPointerException("Null pointer dereference on store")
            }
            ptr.assign(value)
            pc + 1
        }

        is Op.PtrRef -> {
            val frame = ctx.currentFrame()
            frame.subs.push(PtrRecord.Var(frame.vars, op.varIndex))
            pc + 1
        }

        is Op.PtrRefIndex -> {
            val frame = ctx.currentFrame()
            val index = frame.subs.pop().getInt()
            val array = frame.subs.pop().getArray()
            frame.subs.push(PtrRecord.Array(array, index))
            pc + 1
        }

        // ---- Native interop ----

        is Op.NativeCall -> {
            val bound = ctx.natives.getOrNull(op.poolIndex)
                ?: throw IllegalStateException("Native pool entry #${op.poolIndex} is not linked")
            val frame = ctx.currentFrame()

            val jvmArgs = ArrayList<Any?>(op.args.size + 1)
            if (!bound.isConstructor) {
                jvmArgs.add(null) // placeholder for the receiver, filled after args are popped
            }
            for (i in op.args.indices) {
                val record = frame.subs.pop()
                jvmArgs.add(NativeBridge.toJvm(record, op.args[i], bound.jvmParams[i], ctx))
            }
            if (!bound.isConstructor) {
                val receiver = frame.subs.pop()
                jvmArgs[0] = NativeBridge.toJvm(
                    receiver, VmType.Class(bound.ref.className), bound.handle.type().parameterType(0), ctx
                )
            }

            val result = try {
                bound.handle.invokeWithArguments(jvmArgs)
            } catch (ex: Throwable) {
                throw RuntimeException("Native call ${bound.ref} failed: ${ex.message ?: ex}", ex)
            }

            if (bound.isConstructor) {
                frame.subs.push(RefRecord.native(result!!, ctx))
            } else {
                NativeBridge.toVelo(result, ctx)?.let { frame.subs.push(it) }
            }
            pc + 1
        }

        // ---- Actors / futures ----

        is Op.ActorSpawn -> {
            val frame = ctx.currentFrame()
            val cloned = popAndEncodeArgs(frame, op.args, ctx)
            val (handle, rootObjectId) = ActorHandle.spawn(
                runtime = ctx.actorRuntime,
                sharedFrameLoader = ctx.frameLoader,
                sharedNativeRegistry = ctx.nativeRegistry,
                sharedNatives = ctx.natives,
                sharedDataClasses = ctx.dataClasses,
                classFrameNum = op.classFrameNum,
                className = op.className,
                args = cloned,
            )
            frame.subs.push(ActorRefRecord(handle, rootObjectId, op.className))
            pc + 1
        }

        is Op.ActorCall -> {
            val frame = ctx.currentFrame()
            val cloned = popAndEncodeArgs(frame, op.args, ctx)
            val receiver = frame.subs.pop()
            require(receiver is ActorRefRecord) {
                "ActorCall expected actor[T] receiver, got ${receiver::class.simpleName}"
            }
            val future = receiver.handle.requestCallAsync(receiver.objectId, op.methodVarIndex, cloned)
            frame.subs.push(FutureRecord(receiver.handle, future))
            pc + 1
        }

        is Op.FutureAwait -> {
            val frame = ctx.currentFrame()
            val rec = frame.subs.peek()
            require(rec is FutureRecord) {
                "FutureAwait expected future[T] value, got ${rec::class.simpleName}"
            }
            // VEL-11: at the top level of a dispatcher task, a not-yet-ready
            // future yields the fiber rather than blocking the thread. The op
            // stays at this pc (the future is left on the operand stack) and
            // re-runs on resume, when the future is done and the fast path
            // below applies. Nested/inline calls keep blocking semantics —
            // their JVM frames cannot be parked.
            if (!rec.future.isDone && ctx.suspensionEnabled) {
                ctx.requestSuspend(rec.future)
                pc
            } else {
                frame.subs.pop()
                val response = rec.handle.unwrapResponse(rec.future.join())
                frame.subs.push(StructuredClone.decode(response, ctx))
                pc + 1
            }
        }
    }

    /** True when [record] is a `data class` instance (per the program's metadata). */
    private fun isDataInstance(record: Record, ctx: VMContext): Boolean =
        record is RefRecord && record.kind == RefKind.CLASS &&
            ctx.dataClasses.containsKey(record.get<Frame>(ctx).num)

    /**
     * Deep value equality for `data class` instances: same class and every
     * field equal, recursing through nested data classes and arrays. Used only
     * once both operands are known to be data instances; other reference kinds
     * fall back to identity equality.
     */
    private fun deepEquals(a: Record, b: Record, ctx: VMContext): Boolean {
        if (a === b) return true
        if (a is RefRecord && b is RefRecord) {
            if (a.kind != b.kind) return false
            return when (a.kind) {
                RefKind.CLASS -> {
                    val fa = a.get<Frame>(ctx)
                    val fb = b.get<Frame>(ctx)
                    if (fa.num != fb.num) return false
                    val info = ctx.dataClasses[fa.num] ?: return a == b
                    info.fields.all { deepEquals(fa.vars.get(it.index), fb.vars.get(it.index), ctx) }
                }
                RefKind.ARRAY -> {
                    val aa = a.get<Array<Record>>(ctx)
                    val ba = b.get<Array<Record>>(ctx)
                    aa.size == ba.size && aa.indices.all { deepEquals(aa[it], ba[it], ctx) }
                }
                RefKind.NATIVE -> a == b
            }
        }
        return a == b
    }
}
