package vm.operations

import vm.VMContext

import vm.Frame
import vm.SimpleOperation

class Store(
    val index: Int,
): SimpleOperation {

    override fun exec(frame: Frame, ctx: VMContext) {
        val value = frame.subs.pop()
        frame.vars.set(index, value)
    }

}