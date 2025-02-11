package vm.operations

import vm.Frame
import vm.LifoStack
import vm.Operation
import vm.Stack
import java.util.TreeMap

class Call(val args: Int) : Operation {

    override fun exec(pc: Int, stack: Stack<Frame>): Int {
        val thisFrame = stack.peek()
        val addr = thisFrame.subs.pop().getInt()
        val newFrame = Frame(addr = pc + 1, subs = LifoStack(), vars = TreeMap(), parent = thisFrame)
        Array(size = args, init = {
            thisFrame.subs.pop()
        }).reversedArray().forEach { arg ->
            newFrame.subs.push(arg)
        }
        stack.push(newFrame)
        return addr
    }

}