package vm

import core.Op
import core.NativeRegistry

import core.SerializedFrame
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Integration tests for VM execution.
 */
class VMTest {

    // ========== Helper Functions ==========

    private fun createFrame(
        num: Int,
        ops: List<Op>,
        vars: List<Int> = emptyList()
    ): SerializedFrame {
        return SerializedFrame(
            num = num,
            ops = ops.toMutableList(),
            vars = vars
        )
    }

    private fun runVM(frames: List<SerializedFrame>): VMContext {
        // Create context manually for testing
        val stack: Stack<Frame> = LifoStack()
        val frameLoader = GeneralFrameLoader(frames.associateBy { it.num })
        val ctx = VMContext(
            stack = stack,
            frameLoader = frameLoader,
            memory = MemoryAreaImpl(),
            nativeRegistry = NativeRegistry()
        )
        
        val frame = ctx.loadFrame(num = 0, parentVars = null) ?: throw Exception("No main frame")
        ctx.pushFrame(frame)
        
        while (ctx.currentFrame().pc < ctx.currentFrame().ops.size) {
            val currentFrame = ctx.currentFrame()
            val cmd = currentFrame.ops[currentFrame.pc]
            currentFrame.pc = Interpreter.exec(cmd, pc = currentFrame.pc, ctx)
        }
        
        return ctx
    }

    private fun runVMWithHalt(frames: List<SerializedFrame>): VMContext {
        val stack: Stack<Frame> = LifoStack()
        val frameLoader = GeneralFrameLoader(frames.associateBy { it.num })
        val ctx = VMContext(
            stack = stack,
            frameLoader = frameLoader,
            memory = MemoryAreaImpl(),
            nativeRegistry = NativeRegistry()
        )
        
        val frame = ctx.loadFrame(num = 0, parentVars = null) ?: throw Exception("No main frame")
        ctx.pushFrame(frame)
        
        try {
            while (ctx.currentFrame().pc < ctx.currentFrame().ops.size) {
                val currentFrame = ctx.currentFrame()
                val cmd = currentFrame.ops[currentFrame.pc]
                currentFrame.pc = Interpreter.exec(cmd, pc = currentFrame.pc, ctx)
            }
        } catch (_: HaltException) {
            // Expected halt
        }
        
        return ctx
    }

    // ========== Basic Execution Tests ==========

    @Test
    fun `VM executes empty program`() {
        val frames = listOf(
            createFrame(0, emptyList())
        )
        
        val ctx = runVM(frames)
        
        assertTrue(ctx.currentFrame().subs.empty())
    }

    @Test
    fun `VM executes single Push operation`() {
        val frames = listOf(
            createFrame(0, listOf(
                Op.Push(42)
            ))
        )
        
        val ctx = runVM(frames)
        
        assertEquals(42, ctx.currentFrame().subs.pop().getInt())
    }

    @Test
    fun `VM executes multiple Push operations`() {
        val frames = listOf(
            createFrame(0, listOf(
                Op.Push(1),
                Op.Push(2),
                Op.Push(3)
            ))
        )
        
        val ctx = runVM(frames)
        
        assertEquals(3, ctx.currentFrame().subs.pop().getInt())
        assertEquals(2, ctx.currentFrame().subs.pop().getInt())
        assertEquals(1, ctx.currentFrame().subs.pop().getInt())
    }

    // ========== Arithmetic Tests ==========

    @Test
    fun `VM executes addition`() {
        val frames = listOf(
            createFrame(0, listOf(
                Op.Push(10),
                Op.Push(20),
                Op.Add
            ))
        )
        
        val ctx = runVM(frames)
        
        assertEquals(30, ctx.currentFrame().subs.pop().getInt())
    }

    @Test
    fun `VM executes subtraction`() {
        val frames = listOf(
            createFrame(0, listOf(
                Op.Push(50),
                Op.Push(20),
                Op.Sub
            ))
        )
        
        val ctx = runVM(frames)
        
        assertEquals(30, ctx.currentFrame().subs.pop().getInt())
    }

    @Test
    fun `VM executes multiplication`() {
        val frames = listOf(
            createFrame(0, listOf(
                Op.Push(6),
                Op.Push(7),
                Op.Mul
            ))
        )
        
        val ctx = runVM(frames)
        
        assertEquals(42, ctx.currentFrame().subs.pop().getInt())
    }

    @Test
    fun `VM executes division`() {
        val frames = listOf(
            createFrame(0, listOf(
                Op.Push(100),
                Op.Push(4),
                Op.Div
            ))
        )
        
        val ctx = runVM(frames)
        
        assertEquals(25, ctx.currentFrame().subs.pop().getInt())
    }

    @Test
    fun `VM executes complex arithmetic expression`() {
        // Calculate: (10 + 5) * 2 = 30
        val frames = listOf(
            createFrame(0, listOf(
                Op.Push(10),
                Op.Push(5),
                Op.Add,
                Op.Push(2),
                Op.Mul
            ))
        )
        
        val ctx = runVM(frames)
        
        assertEquals(30, ctx.currentFrame().subs.pop().getInt())
    }

    // ========== Variable Tests ==========

    @Test
    fun `VM stores and loads variable`() {
        val frames = listOf(
            createFrame(0, listOf(
                Op.Push(42),
                Op.Store(0),
                Op.Load(0)
            ), vars = listOf(0))
        )
        
        val ctx = runVM(frames)
        
        assertEquals(42, ctx.currentFrame().subs.pop().getInt())
    }

    @Test
    fun `VM stores and loads multiple variables`() {
        val frames = listOf(
            createFrame(0, listOf(
                Op.Push(10),
                Op.Store(0),
                Op.Push(20),
                Op.Store(1),
                Op.Load(0),
                Op.Load(1),
                Op.Add
            ), vars = listOf(0, 1))
        )
        
        val ctx = runVM(frames)
        
        assertEquals(30, ctx.currentFrame().subs.pop().getInt())
    }

    @Test
    fun `VM modifies variable`() {
        val frames = listOf(
            createFrame(0, listOf(
                Op.Push(10),
                Op.Store(0),
                Op.Push(5),
                Op.Store(0),
                Op.Load(0)
            ), vars = listOf(0))
        )
        
        val ctx = runVM(frames)
        
        assertEquals(5, ctx.currentFrame().subs.pop().getInt())
    }

    // ========== Conditional Tests ==========

    @Test
    fun `VM executes If - true branch`() {
        val frames = listOf(
            createFrame(0, listOf(
                Op.Push(true),
                Op.If(elseSkip = 2),  // If true, continue; else skip 2 ops
                Op.Push(100),         // True branch
                Op.Move(1),           // Skip false branch
                Op.Push(200)          // False branch (skipped)
            ))
        )
        
        val ctx = runVM(frames)
        
        assertEquals(100, ctx.currentFrame().subs.pop().getInt())
    }

    @Test
    fun `VM executes If - false branch`() {
        val frames = listOf(
            createFrame(0, listOf(
                Op.Push(false),
                Op.If(elseSkip = 2),  // If false, skip 2 ops
                Op.Push(100),         // True branch (skipped)
                Op.Move(1),           // Skip false branch (skipped)
                Op.Push(200)          // False branch
            ))
        )
        
        val ctx = runVM(frames)
        
        assertEquals(200, ctx.currentFrame().subs.pop().getInt())
    }

    @Test
    fun `VM executes comparison and conditional`() {
        // If 10 > 5 then push 1 else push 0
        val frames = listOf(
            createFrame(0, listOf(
                Op.Push(10),
                Op.Push(5),
                Op.More,            // 10 > 5 = true
                Op.If(elseSkip = 2),
                Op.Push(1),           // True branch
                Op.Move(1),
                Op.Push(0)            // False branch
            ))
        )
        
        val ctx = runVM(frames)
        
        assertEquals(1, ctx.currentFrame().subs.pop().getInt())
    }

    // ========== Function Call Tests ==========

    @Test
    fun `VM calls function and returns`() {
        val frames = listOf(
            // Main frame (0)
            createFrame(0, listOf(
                Op.Push(1),           // Frame number to call
                Op.Call(args = 0),
                // After return, result should be on stack
            )),
            // Function frame (1)
            createFrame(1, listOf(
                Op.Push(42),
                Op.Ret
            ))
        )
        
        val ctx = runVM(frames)
        
        assertEquals(42, ctx.currentFrame().subs.pop().getInt())
    }

    @Test
    fun `VM calls function with arguments`() {
        val frames = listOf(
            // Main frame (0)
            createFrame(0, listOf(
                Op.Push(10),          // Argument
                Op.Push(20),          // Argument
                Op.Push(1),           // Frame number to call
                Op.Call(args = 2),
            )),
            // Function frame (1) - adds two arguments
            createFrame(1, listOf(
                Op.Store(0),          // Store first arg
                Op.Store(1),          // Store second arg
                Op.Load(0),
                Op.Load(1),
                Op.Add,
                Op.Ret
            ), vars = listOf(0, 1))
        )
        
        val ctx = runVM(frames)
        
        assertEquals(30, ctx.currentFrame().subs.pop().getInt())
    }

    @Test
    fun `VM calls nested functions`() {
        val frames = listOf(
            // Main frame (0)
            createFrame(0, listOf(
                Op.Push(1),           // Call first function
                Op.Call(args = 0),
            )),
            // Function 1 - calls function 2
            createFrame(1, listOf(
                Op.Push(2),           // Call second function
                Op.Call(args = 0),
                Op.Push(10),          // Add 10 to result
                Op.Add,
                Op.Ret
            )),
            // Function 2 - returns 5
            createFrame(2, listOf(
                Op.Push(5),
                Op.Ret
            ))
        )
        
        val ctx = runVM(frames)
        
        // Function 2 returns 5, function 1 adds 10 = 15
        assertEquals(15, ctx.currentFrame().subs.pop().getInt())
    }

    // ========== Halt Tests ==========

    @Test
    fun `VM halts execution`() {
        val frames = listOf(
            createFrame(0, listOf(
                Op.Push(1),
                Op.Halt,
                Op.Push(2)  // Should not be executed
            ))
        )
        
        val ctx = runVMWithHalt(frames)
        
        // Only 1 should be on stack (2 was not pushed because of halt)
        assertEquals(1, ctx.currentFrame().subs.pop().getInt())
        assertTrue(ctx.currentFrame().subs.empty())
    }

    // ========== Stack Manipulation Tests ==========

    @Test
    fun `VM executes Dup operation`() {
        val frames = listOf(
            createFrame(0, listOf(
                Op.Push(42),
                Op.Dup
            ))
        )
        
        val ctx = runVM(frames)
        
        assertEquals(42, ctx.currentFrame().subs.pop().getInt())
        assertEquals(42, ctx.currentFrame().subs.pop().getInt())
    }

    @Test
    fun `VM executes Swap operation`() {
        val frames = listOf(
            createFrame(0, listOf(
                Op.Push(1),
                Op.Push(2),
                Op.Swap
            ))
        )
        
        val ctx = runVM(frames)
        
        // After swap: [2, 1] where 1 is on top
        assertEquals(1, ctx.currentFrame().subs.pop().getInt())
        assertEquals(2, ctx.currentFrame().subs.pop().getInt())
    }

    @Test
    fun `VM executes Pop operation`() {
        val frames = listOf(
            createFrame(0, listOf(
                Op.Push(1),
                Op.Push(2),
                Op.Pop
            ))
        )
        
        val ctx = runVM(frames)
        
        assertEquals(1, ctx.currentFrame().subs.pop().getInt())
        assertTrue(ctx.currentFrame().subs.empty())
    }

    // ========== String Operations Tests ==========

    @Test
    fun `VM executes string concatenation`() {
        val frames = listOf(
            createFrame(0, listOf(
                Op.Push("Hello, "),
                Op.Push("World!"),
                Op.StrCon
            ))
        )
        
        val ctx = runVM(frames)
        
        assertEquals("Hello, World!", ctx.currentFrame().subs.pop().getString())
    }

    @Test
    fun `VM executes string length`() {
        val frames = listOf(
            createFrame(0, listOf(
                Op.Push("Hello"),
                Op.StrLen
            ))
        )
        
        val ctx = runVM(frames)
        
        assertEquals(5, ctx.currentFrame().subs.pop().getInt())
    }

    @Test
    fun `VM executes int to string conversion`() {
        val frames = listOf(
            createFrame(0, listOf(
                Op.Push(42),
                Op.NumStr
            ))
        )
        
        val ctx = runVM(frames)
        
        assertEquals("42", ctx.currentFrame().subs.pop().getString())
    }

    // ========== Logical Operations Tests ==========

    @Test
    fun `VM executes Equals - true`() {
        val frames = listOf(
            createFrame(0, listOf(
                Op.Push(42),
                Op.Push(42),
                Op.Equals
            ))
        )
        
        val ctx = runVM(frames)
        
        assertTrue(ctx.currentFrame().subs.pop().getBool())
    }

    @Test
    fun `VM executes Equals - false`() {
        val frames = listOf(
            createFrame(0, listOf(
                Op.Push(42),
                Op.Push(43),
                Op.Equals
            ))
        )
        
        val ctx = runVM(frames)
        
        assertEquals(false, ctx.currentFrame().subs.pop().getBool())
    }

    @Test
    fun `VM executes More - true`() {
        val frames = listOf(
            createFrame(0, listOf(
                Op.Push(10),
                Op.Push(5),
                Op.More
            ))
        )
        
        val ctx = runVM(frames)
        
        assertTrue(ctx.currentFrame().subs.pop().getBool())
    }

    @Test
    fun `VM executes And operation`() {
        val frames = listOf(
            createFrame(0, listOf(
                Op.Push(0b1010),
                Op.Push(0b1100),
                Op.And
            ))
        )
        
        val ctx = runVM(frames)
        
        assertEquals(0b1000, ctx.currentFrame().subs.pop().getInt())
    }

    @Test
    fun `VM executes Or operation`() {
        val frames = listOf(
            createFrame(0, listOf(
                Op.Push(0b1010),
                Op.Push(0b1100),
                Op.Or
            ))
        )
        
        val ctx = runVM(frames)
        
        assertEquals(0b1110, ctx.currentFrame().subs.pop().getInt())
    }

    // ========== Integration Tests ==========

    @Test
    fun `VM computes factorial iteratively`() {
        // Compute 5! = 120 iteratively
        // result = 1
        // i = 5
        // while (i > 0) { result = result * i; i = i - 1 }
        val frames = listOf(
            createFrame(0, listOf(
                // 0: result = 1
                Op.Push(1),           // 0
                Op.Store(0),          // 1
                // i = 5
                Op.Push(5),           // 2
                Op.Store(1),          // 3
                // Loop start (pc = 4)
                // Check i > 0
                Op.Load(1),           // 4
                Op.Push(0),           // 5
                Op.More,            // 6
                Op.If(elseSkip = 9),  // 7: If false, skip to Op.Load(0) at 17
                // result = result * i
                Op.Load(0),           // 8
                Op.Load(1),           // 9
                Op.Mul,             // 10
                Op.Store(0),          // 11
                // i = i - 1
                Op.Load(1),           // 12
                Op.Push(1),           // 13
                Op.Sub,             // 14
                Op.Store(1),          // 15
                // Jump back to loop start: Move returns pc + count + 1 = 16 + (-13) + 1 = 4
                Op.Move(-13),         // 16
                // Load result
                Op.Load(0)            // 17
            ), vars = listOf(0, 1))
        )
        
        val ctx = runVM(frames)
        
        assertEquals(120, ctx.currentFrame().subs.pop().getInt())
    }

    @Test
    fun `VM computes sum using function call`() {
        // sum(a, b) = a + b
        // main: sum(15, 27) = 42
        val frames = listOf(
            createFrame(0, listOf(
                Op.Push(15),
                Op.Push(27),
                Op.Push(1),
                Op.Call(args = 2)
            )),
            createFrame(1, listOf(
                Op.Store(0),
                Op.Store(1),
                Op.Load(0),
                Op.Load(1),
                Op.Add,
                Op.Ret
            ), vars = listOf(0, 1))
        )
        
        val ctx = runVM(frames)
        
        assertEquals(42, ctx.currentFrame().subs.pop().getInt())
    }

    // ========== Closure / FuncRecord Tests ==========

    /**
     * Escaping closure: a lambda is created in an inner scope, returned to the
     * outer scope, and invoked there. The lambda must still be able to read
     * `n` from the (now-returned-from) frame that defined it.
     *
     * Velo equivalent (which the type system can't express today, hence this
     * lower-level reconstruction):
     *
     *   func makeAdder(int n) ... {
     *       func(int x) int { x + n }   # captures n
     *   }
     *   any add5 = makeAdder(5)
     *   add5(3)                          # = 8
     *
     * Without FuncRecord (plain ValueRecord(Int) frame numbers + caller-as-parent
     * scoping), the inner Op.Load(1) would walk main's vars chain — which doesn't
     * contain n — and crash with "Undefined variable 1".
     *
     * With FuncRecord, the function captures the defining frame's Vars at
     * creation time, so n is reachable when the lambda is invoked from main.
     */
    @Test
    fun `VM resolves escaping closure via captured vars`() {
        val frames = listOf(
            // Frame 0: main
            //   var[0] is the slot that stores the returned FuncRecord
            createFrame(0, listOf(
                Op.Push(5),                   // arg n=5 for makeAdder
                Op.Frame(num = 1),          // push FuncRecord(1, mainVars)
                Op.Call(args = 1),            // call makeAdder(5) -> FuncRecord(2, makeAdderVars)
                Op.Store(0),                  // save returned closure into main.var[0]
                Op.Push(3),                   // arg x=3 for the closure
                Op.Load(0),                   // push the closure
                Op.Call(args = 1)             // invoke it; result (8) ends up on main's stack
            ), vars = listOf(0)),

            // Frame 1: makeAdder body
            //   var[1] holds parameter n
            createFrame(1, listOf(
                Op.Store(1),                  // store arg n
                Op.Frame(num = 2),          // push FuncRecord(2, makeAdderVars containing n)
                Op.Ret                      // return the closure
            ), vars = listOf(1)),

            // Frame 2: inner lambda body
            //   var[2] holds parameter x; n is reached via captured vars chain
            createFrame(2, listOf(
                Op.Store(2),                  // store arg x
                Op.Load(2),                   // x
                Op.Load(1),                   // n - resolved through FuncRecord.capturedVars
                Op.Add,
                Op.Ret
            ), vars = listOf(2))
        )

        val ctx = runVM(frames)

        assertEquals(8, ctx.currentFrame().subs.pop().getInt())
    }

    /**
     * Two independent closures created from the same `makeAdder` factory must
     * each carry their own captured `n`. This guards against accidental
     * sharing of the captured Vars chain between sibling closures.
     */
    @Test
    fun `VM keeps captured vars distinct across closure instances`() {
        // Same shape as the test above, but produces two closures with
        // different captured n values, then calls each independently.
        // var[0] = first closure (captures n=5), var[3] = second (captures n=10)
        val frames = listOf(
            createFrame(0, listOf(
                // add5 = makeAdder(5)
                Op.Push(5),
                Op.Frame(num = 1),
                Op.Call(args = 1),
                Op.Store(0),
                // add10 = makeAdder(10)
                Op.Push(10),
                Op.Frame(num = 1),
                Op.Call(args = 1),
                Op.Store(3),
                // add5(3) -> 8
                Op.Push(3),
                Op.Load(0),
                Op.Call(args = 1),
                // add10(3) -> 13
                Op.Push(3),
                Op.Load(3),
                Op.Call(args = 1),
                Op.Add                      // sum: 8 + 13 = 21
            ), vars = listOf(0, 3)),

            createFrame(1, listOf(
                Op.Store(1),
                Op.Frame(num = 2),
                Op.Ret
            ), vars = listOf(1)),

            createFrame(2, listOf(
                Op.Store(2),
                Op.Load(2),
                Op.Load(1),
                Op.Add,
                Op.Ret
            ), vars = listOf(2))
        )

        val ctx = runVM(frames)

        assertEquals(21, ctx.currentFrame().subs.pop().getInt())
    }
}
