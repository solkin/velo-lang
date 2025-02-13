package vm.operations

import vm.Frame
import vm.SimpleOperation

class Def(
    val index: Int,
): SimpleOperation {

    override fun exec(frame: Frame) {
        val value = frame.subs.pop()
        frame.vars.def(index, value)
    }

}