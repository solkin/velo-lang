package vm.operations

import vm.Operation
import vm.VMContext
import vm.records.ClassRecord
import vm.records.NativeRecord

/**
 * Wraps a JVM object (from LinkRecord) into a full Velo class instance.
 * 
 * This operation:
 * 1. Pops the JVM object from the stack
 * 2. Loads the class frame and executes its body (to initialize methods)
 * 3. Replaces the native instance with the provided JVM object
 * 4. Returns a ClassRecord
 * 
 * @param classFrameNum The frame number of the target class
 * @param nativeInstanceIndex The index where native instance is stored in class frame
 */
class NativeWrap(
    val classFrameNum: Int,
    val nativeInstanceIndex: Int
) : Operation {

    override fun exec(pc: Int, ctx: VMContext): Int {
        val frame = ctx.currentFrame()
        
        // Pop the JVM object from the stack (it's in a LinkRecord from NativeInvoke)
        val jvmObject = frame.subs.pop().get<Any>()
        
        // Load the class frame
        val classFrame = ctx.loadFrame(num = classFrameNum, parent = frame)
            ?: throw Exception("Class frame $classFrameNum not found")
        
        // Execute the class frame to initialize methods
        // We need to run the class body without creating a new native instance
        ctx.pushFrame(classFrame)
        
        // Run class initialization
        while (classFrame.pc < classFrame.ops.size) {
            val cmd = classFrame.ops[classFrame.pc]
            
            // Skip NativeConstructor - we already have the object
            if (cmd is NativeConstructor) {
                // Instead of creating new instance, use the provided one
                val nativeRecord = NativeRecord.create(jvmObject, ctx)
                classFrame.subs.push(nativeRecord)
                classFrame.pc++
                continue
            }
            
            // Stop before Instance - we'll create our own ClassRecord
            if (cmd is Instance) {
                break
            }
            
            classFrame.pc = cmd.exec(pc = classFrame.pc, ctx)
        }
        
        // Pop the class frame (we manually executed it)
        ctx.popFrame()
        
        // Create ClassRecord with the class frame and native index
        val record = ClassRecord.create(classFrame, nativeInstanceIndex, ctx)
        frame.subs.push(record)
        
        return pc + 1
    }
}

