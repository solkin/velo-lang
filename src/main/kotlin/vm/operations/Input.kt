package vm.operations

import vm.Frame
import vm.SimpleOperation
import vm.records.ValueRecord
import java.util.Scanner

class Input : SimpleOperation {

    override fun exec(frame: Frame) {
        val str = Scanner(System.`in`).nextLine()

        val rec = ValueRecord(str)

        frame.subs.push(rec)
    }

}