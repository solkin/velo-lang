package vm

import java.io.OutputStream

interface Buffer {

    fun offset(): Int

    fun output(): OutputStream

}