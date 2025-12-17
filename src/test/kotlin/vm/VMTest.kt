package vm

import utils.SerializedFrame
import vm.operations.Add
import vm.operations.And
import vm.operations.Call
import vm.operations.Div
import vm.operations.Dup
import vm.operations.Equals
import vm.operations.Halt
import vm.operations.If
import vm.operations.IntStr
import vm.operations.Load
import vm.operations.More
import vm.operations.Move
import vm.operations.Mul
import vm.operations.Or
import vm.operations.Pop
import vm.operations.Push
import vm.operations.Ret
import vm.operations.Store
import vm.operations.StrCon
import vm.operations.StrInt
import vm.operations.StrLen
import vm.operations.Sub
import vm.operations.Swap
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
        ops: List<Operation>,
        vars: List<Int> = emptyList()
    ): SerializedFrame {
        return SerializedFrame(
            num = num,
            ops = ops.toMutableList(),
            vars = vars
        )
    }

    private fun runVM(frames: List<SerializedFrame>): VMContext {
        val vm = VM()
        vm.load(SimpleParser(frames))
        
        // Create context manually for testing
        val stack: Stack<Frame> = LifoStack()
        val frameLoader = GeneralFrameLoader(frames.associateBy { it.num })
        val ctx = VMContext(
            stack = stack,
            frameLoader = frameLoader,
            heap = HeapImpl(),
            nativeArea = NativeImpl(),
            nativeRegistry = NativeRegistry()
        )
        
        val frame = ctx.loadFrame(num = 0, parent = null) ?: throw Exception("No main frame")
        ctx.pushFrame(frame)
        
        while (ctx.currentFrame().pc < ctx.currentFrame().ops.size) {
            val currentFrame = ctx.currentFrame()
            val cmd = currentFrame.ops[currentFrame.pc]
            currentFrame.pc = cmd.exec(pc = currentFrame.pc, ctx)
        }
        
        return ctx
    }

    private fun runVMWithHalt(frames: List<SerializedFrame>): VMContext {
        val stack: Stack<Frame> = LifoStack()
        val frameLoader = GeneralFrameLoader(frames.associateBy { it.num })
        val ctx = VMContext(
            stack = stack,
            frameLoader = frameLoader,
            heap = HeapImpl(),
            nativeArea = NativeImpl(),
            nativeRegistry = NativeRegistry()
        )
        
        val frame = ctx.loadFrame(num = 0, parent = null) ?: throw Exception("No main frame")
        ctx.pushFrame(frame)
        
        try {
            while (ctx.currentFrame().pc < ctx.currentFrame().ops.size) {
                val currentFrame = ctx.currentFrame()
                val cmd = currentFrame.ops[currentFrame.pc]
                currentFrame.pc = cmd.exec(pc = currentFrame.pc, ctx)
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
                Push(42)
            ))
        )
        
        val ctx = runVM(frames)
        
        assertEquals(42, ctx.currentFrame().subs.pop().getInt())
    }

    @Test
    fun `VM executes multiple Push operations`() {
        val frames = listOf(
            createFrame(0, listOf(
                Push(1),
                Push(2),
                Push(3)
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
                Push(10),
                Push(20),
                Add()
            ))
        )
        
        val ctx = runVM(frames)
        
        assertEquals(30, ctx.currentFrame().subs.pop().getInt())
    }

    @Test
    fun `VM executes subtraction`() {
        val frames = listOf(
            createFrame(0, listOf(
                Push(50),
                Push(20),
                Sub()
            ))
        )
        
        val ctx = runVM(frames)
        
        assertEquals(30, ctx.currentFrame().subs.pop().getInt())
    }

    @Test
    fun `VM executes multiplication`() {
        val frames = listOf(
            createFrame(0, listOf(
                Push(6),
                Push(7),
                Mul()
            ))
        )
        
        val ctx = runVM(frames)
        
        assertEquals(42, ctx.currentFrame().subs.pop().getInt())
    }

    @Test
    fun `VM executes division`() {
        val frames = listOf(
            createFrame(0, listOf(
                Push(100),
                Push(4),
                Div()
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
                Push(10),
                Push(5),
                Add(),
                Push(2),
                Mul()
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
                Push(42),
                Store(0),
                Load(0)
            ), vars = listOf(0))
        )
        
        val ctx = runVM(frames)
        
        assertEquals(42, ctx.currentFrame().subs.pop().getInt())
    }

    @Test
    fun `VM stores and loads multiple variables`() {
        val frames = listOf(
            createFrame(0, listOf(
                Push(10),
                Store(0),
                Push(20),
                Store(1),
                Load(0),
                Load(1),
                Add()
            ), vars = listOf(0, 1))
        )
        
        val ctx = runVM(frames)
        
        assertEquals(30, ctx.currentFrame().subs.pop().getInt())
    }

    @Test
    fun `VM modifies variable`() {
        val frames = listOf(
            createFrame(0, listOf(
                Push(10),
                Store(0),
                Push(5),
                Store(0),
                Load(0)
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
                Push(true),
                If(elseSkip = 2),  // If true, continue; else skip 2 ops
                Push(100),         // True branch
                Move(1),           // Skip false branch
                Push(200)          // False branch (skipped)
            ))
        )
        
        val ctx = runVM(frames)
        
        assertEquals(100, ctx.currentFrame().subs.pop().getInt())
    }

    @Test
    fun `VM executes If - false branch`() {
        val frames = listOf(
            createFrame(0, listOf(
                Push(false),
                If(elseSkip = 2),  // If false, skip 2 ops
                Push(100),         // True branch (skipped)
                Move(1),           // Skip false branch (skipped)
                Push(200)          // False branch
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
                Push(10),
                Push(5),
                More(),            // 10 > 5 = true
                If(elseSkip = 2),
                Push(1),           // True branch
                Move(1),
                Push(0)            // False branch
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
                Push(1),           // Frame number to call
                Call(args = 0),
                // After return, result should be on stack
            )),
            // Function frame (1)
            createFrame(1, listOf(
                Push(42),
                Ret()
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
                Push(10),          // Argument
                Push(20),          // Argument
                Push(1),           // Frame number to call
                Call(args = 2),
            )),
            // Function frame (1) - adds two arguments
            createFrame(1, listOf(
                Store(0),          // Store first arg
                Store(1),          // Store second arg
                Load(0),
                Load(1),
                Add(),
                Ret()
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
                Push(1),           // Call first function
                Call(args = 0),
            )),
            // Function 1 - calls function 2
            createFrame(1, listOf(
                Push(2),           // Call second function
                Call(args = 0),
                Push(10),          // Add 10 to result
                Add(),
                Ret()
            )),
            // Function 2 - returns 5
            createFrame(2, listOf(
                Push(5),
                Ret()
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
                Push(1),
                Halt(),
                Push(2)  // Should not be executed
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
                Push(42),
                Dup()
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
                Push(1),
                Push(2),
                Swap()
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
                Push(1),
                Push(2),
                Pop()
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
                Push("Hello, "),
                Push("World!"),
                StrCon()
            ))
        )
        
        val ctx = runVM(frames)
        
        assertEquals("Hello, World!", ctx.currentFrame().subs.pop().getString())
    }

    @Test
    fun `VM executes string length`() {
        val frames = listOf(
            createFrame(0, listOf(
                Push("Hello"),
                StrLen()
            ))
        )
        
        val ctx = runVM(frames)
        
        assertEquals(5, ctx.currentFrame().subs.pop().getInt())
    }

    @Test
    fun `VM executes int to string conversion`() {
        val frames = listOf(
            createFrame(0, listOf(
                Push(42),
                IntStr()
            ))
        )
        
        val ctx = runVM(frames)
        
        assertEquals("42", ctx.currentFrame().subs.pop().getString())
    }

    @Test
    fun `VM executes string to int conversion`() {
        val frames = listOf(
            createFrame(0, listOf(
                Push("123"),
                StrInt()
            ))
        )
        
        val ctx = runVM(frames)
        
        assertEquals(123, ctx.currentFrame().subs.pop().getInt())
    }

    // ========== Logical Operations Tests ==========

    @Test
    fun `VM executes Equals - true`() {
        val frames = listOf(
            createFrame(0, listOf(
                Push(42),
                Push(42),
                Equals()
            ))
        )
        
        val ctx = runVM(frames)
        
        assertTrue(ctx.currentFrame().subs.pop().getBool())
    }

    @Test
    fun `VM executes Equals - false`() {
        val frames = listOf(
            createFrame(0, listOf(
                Push(42),
                Push(43),
                Equals()
            ))
        )
        
        val ctx = runVM(frames)
        
        assertEquals(false, ctx.currentFrame().subs.pop().getBool())
    }

    @Test
    fun `VM executes More - true`() {
        val frames = listOf(
            createFrame(0, listOf(
                Push(10),
                Push(5),
                More()
            ))
        )
        
        val ctx = runVM(frames)
        
        assertTrue(ctx.currentFrame().subs.pop().getBool())
    }

    @Test
    fun `VM executes And operation`() {
        val frames = listOf(
            createFrame(0, listOf(
                Push(0b1010),
                Push(0b1100),
                And()
            ))
        )
        
        val ctx = runVM(frames)
        
        assertEquals(0b1000, ctx.currentFrame().subs.pop().getInt())
    }

    @Test
    fun `VM executes Or operation`() {
        val frames = listOf(
            createFrame(0, listOf(
                Push(0b1010),
                Push(0b1100),
                Or()
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
                Push(1),           // 0
                Store(0),          // 1
                // i = 5
                Push(5),           // 2
                Store(1),          // 3
                // Loop start (pc = 4)
                // Check i > 0
                Load(1),           // 4
                Push(0),           // 5
                More(),            // 6
                If(elseSkip = 9),  // 7: If false, skip to Load(0) at 17
                // result = result * i
                Load(0),           // 8
                Load(1),           // 9
                Mul(),             // 10
                Store(0),          // 11
                // i = i - 1
                Load(1),           // 12
                Push(1),           // 13
                Sub(),             // 14
                Store(1),          // 15
                // Jump back to loop start: Move returns pc + count + 1 = 16 + (-13) + 1 = 4
                Move(-13),         // 16
                // Load result
                Load(0)            // 17
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
                Push(15),
                Push(27),
                Push(1),
                Call(args = 2)
            )),
            createFrame(1, listOf(
                Store(0),
                Store(1),
                Load(0),
                Load(1),
                Add(),
                Ret()
            ), vars = listOf(0, 1))
        )
        
        val ctx = runVM(frames)
        
        assertEquals(42, ctx.currentFrame().subs.pop().getInt())
    }
}

