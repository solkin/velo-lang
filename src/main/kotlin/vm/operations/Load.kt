package vm.operations

import vm.Frame
import vm.SimpleOperation

class Load(
    val index: Int,
): SimpleOperation {

    override fun exec(frame: Frame) {
        val rec = frame.vars.get(index)
        frame.subs.push(rec)
    }

}