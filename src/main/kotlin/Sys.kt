import java.util.Scanner

class Sys(val pid: Int) {
    fun print(text: String): String {
        kotlin.io.print(text)
        return text
    }
    fun input(): String {
        return Scanner(System.`in`).nextLine()
    }
}