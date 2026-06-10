import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.net.URI
import java.time.Duration

class Http() {
    private val client: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build()
    
    private var lastStatusCode: Int = 200

    /**
     * Выполняет HTTP GET запрос
     * @param url URL для запроса
     * @return Тело ответа как строка
     */
    fun get(url: String): String {
        val request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .GET()
            .timeout(Duration.ofSeconds(30))
            .build()

        val response = client.send(request, HttpResponse.BodyHandlers.ofString())
        lastStatusCode = response.statusCode()
        return response.body()
    }

    /**
     * Выполняет HTTP POST запрос
     * @param url URL для запроса
     * @param body Тело запроса
     * @param contentType Content-Type заголовок. Если пустая строка, используется "application/json"
     * @return Тело ответа как строка
     */
    fun post(url: String, body: String, contentType: String): String {
        val actualContentType = if (contentType.isEmpty()) "application/json" else contentType
        
        val request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .header("Content-Type", actualContentType)
            .timeout(Duration.ofSeconds(30))
            .build()

        val response = client.send(request, HttpResponse.BodyHandlers.ofString())
        lastStatusCode = response.statusCode()
        return response.body()
    }

    /**
     * Получает код статуса последнего запроса
     * @return Код статуса HTTP (200, 404, 500 и т.д.)
     */
    fun statusCode(): Int {
        return lastStatusCode
    }
}

