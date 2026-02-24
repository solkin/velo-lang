import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.ServerSocket
import java.net.Socket as JSocket

class Socket() {
    private var socket: JSocket? = null
    private var server: ServerSocket? = null
    private var reader: BufferedReader? = null
    private var writer: PrintWriter? = null

    fun connect(host: String, port: Int) {
        val s = JSocket(host, port)
        attach(s)
    }

    fun bind(port: Int) {
        server = ServerSocket(port)
    }

    fun accept(): Socket {
        val s = server ?: throw IllegalStateException("Socket is not bound")
        val client = Socket()
        client.attach(s.accept())
        return client
    }

    fun write(data: String) {
        val w = writer ?: throw IllegalStateException("Socket is not connected")
        w.print(data)
        w.flush()
    }

    fun writeLine(data: String) {
        val w = writer ?: throw IllegalStateException("Socket is not connected")
        w.println(data)
        w.flush()
    }

    fun writeBytes(data: Array<Byte>) {
        val s = socket ?: throw IllegalStateException("Socket is not connected")
        s.outputStream.write(data.toByteArray())
        s.outputStream.flush()
    }

    fun readLine(): String {
        val r = reader ?: throw IllegalStateException("Socket is not connected")
        return r.readLine() ?: ""
    }

    fun read(size: Int): String {
        val r = reader ?: throw IllegalStateException("Socket is not connected")
        val buf = CharArray(size)
        val n = r.read(buf, 0, size)
        return if (n > 0) String(buf, 0, n) else ""
    }

    fun readBytes(size: Int): ByteArray {
        val s = socket ?: throw IllegalStateException("Socket is not connected")
        val buf = ByteArray(size)
        val n = s.inputStream.read(buf, 0, size)
        return if (n > 0) buf.copyOf(n) else ByteArray(0)
    }

    fun available(): Int {
        val s = socket ?: return 0
        return s.inputStream.available()
    }

    fun connected(): Boolean {
        val s = socket ?: return false
        return s.isConnected && !s.isClosed
    }

    fun close() {
        reader?.close()
        writer?.close()
        socket?.close()
        server?.close()
    }

    fun remoteAddress(): String {
        val s = socket ?: return ""
        return s.inetAddress.hostAddress
    }

    fun remotePort(): Int {
        val s = socket ?: return 0
        return s.port
    }

    fun setTimeout(millis: Int) {
        socket?.soTimeout = millis
        server?.soTimeout = millis
    }

    private fun attach(s: JSocket) {
        socket = s
        reader = BufferedReader(InputStreamReader(s.inputStream))
        writer = PrintWriter(s.outputStream, false)
    }
}
