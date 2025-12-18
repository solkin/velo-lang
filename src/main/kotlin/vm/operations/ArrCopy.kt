package vm.operations

import vm.VMContext

import vm.Frame
import vm.SimpleOperation

class ArrCopy : SimpleOperation {

    override fun exec(frame: Frame, ctx: VMContext) {
        val srcPos = frame.subs.pop().getInt()
        val dstPos = frame.subs.pop().getInt()
        val length = frame.subs.pop().getInt()
        val src = frame.subs.pop().getArray()
        val dst = frame.subs.pop().getArray()

        System.arraycopy(src, srcPos, dst, dstPos, length)
    }

}
