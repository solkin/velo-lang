package vm

import core.NativeRegistry

import core.Op

import core.SerializedFrame
import vm.records.EmptyRecord
import java.io.ByteArrayOutputStream
import java.io.PrintStream

/**
 * Test utilities for VM testing.
 */
object TestUtils {
    
    /**
     * Create a test VMContext with minimal setup.
     */
    fun createTestContext(
        frames: Map<Int, SerializedFrame> = emptyMap()
    ): VMContext {
        val stack = LifoStack<Frame>()
        val frame = Frame(
            pc = 0,
            subs = LifoStack(),
            vars = createVars(emptyList(), null),
            ops = emptyList()
        )
        stack.push(frame)
        
        return VMContext(
            stack = stack,
            frameLoader = GeneralFrameLoader(frames),
            memory = MemoryAreaImpl(),
            nativeRegistry = NativeRegistry()
        )
    }
    
    /**
     * Create a test Frame with empty stack and vars.
     */
    fun createTestFrame(
        ops: List<Op> = emptyList(),
        parentVars: Vars? = null
    ): Frame {
        return Frame(
            pc = 0,
            subs = LifoStack(),
            vars = createVars(emptyList(), parentVars),
            ops = ops
        )
    }
    
    /**
     * Capture stdout output from a block.
     */
    fun captureOutput(block: () -> Unit): String {
        val baos = ByteArrayOutputStream()
        val old = System.out
        System.setOut(PrintStream(baos))
        try {
            block()
        } finally {
            System.setOut(old)
        }
        return baos.toString()
    }
    
    /**
     * Create a simple SerializedFrame for testing.
     */
    fun createSerializedFrame(
        num: Int,
        ops: List<Op> = emptyList(),
        vars: List<Int> = emptyList()
    ): SerializedFrame {
        return SerializedFrame(
            num = num,
            ops = ops.toMutableList(),
            vars = vars
        )
    }
}
