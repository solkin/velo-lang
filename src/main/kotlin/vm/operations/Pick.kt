package vm.operations

import vm.Frame
import vm.OperationNotSupportedException
import vm.SimpleOperation

class Pick: SimpleOperation {

    override fun exec(frame: Frame) {
        throw OperationNotSupportedException()
    }

}