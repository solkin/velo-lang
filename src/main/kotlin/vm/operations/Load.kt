package vm.operations

import vm.VMContext

import vm.Frame
import vm.SimpleOperation

class Load(
    val index: Int,
): SimpleOperation {

    override fun exec(frame: Frame, ctx: VMContext) {
        val rec = frame.vars.get(index)
        frame.subs.push(rec)
    }

}