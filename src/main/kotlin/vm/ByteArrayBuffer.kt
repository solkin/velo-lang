package vm

import java.io.ByteArrayOutputStream

class ByteArrayBuffer(
    private val offset: Int,
): Buffer {

    private val output = ByteArrayOutputStream()

    override fun offset() = offset + output.size()

    override fun output() = output

}