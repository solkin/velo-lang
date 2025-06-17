import java.util.Scanner

class Terminal() {
    fun print(text: String): String {
        kotlin.io.print(text)
        return text
    }
    fun input(): String {
        return Scanner(System.`in`).nextLine()
    }
}
