package vm.operations

import vm.VMContext

import vm.Frame
import vm.SimpleOperation

class DictSet : SimpleOperation {

    override fun exec(frame: Frame, ctx: VMContext) {
        val key = frame.subs.pop()
        val dict = frame.subs.pop().getDict()
        val value = frame.subs.pop()

        dict[key] = value
    }

}
