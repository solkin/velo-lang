package vm.operations

import vm.Frame
import vm.SimpleOperation

class StructElement : SimpleOperation {

    override fun exec(frame: Frame) {
        val index = frame.subs.pop().getInt()
        val struct = frame.subs.pop().getStruct()

        frame.subs.push(struct[index])
    }

}
