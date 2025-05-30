package vm.operations

import vm.Frame
import vm.SimpleOperation
import vm.records.FrameRecord

class FrameNum(): SimpleOperation {

    override fun exec(frame: Frame) {
        val frame = frame.subs.pop().getFrame()
        frame.subs.push(FrameRecord(frame.pc))
    }

}