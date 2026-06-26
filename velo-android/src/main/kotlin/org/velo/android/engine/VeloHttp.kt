package org.velo.android.engine

import java.net.HttpURLConnection
import java.net.URL

/**
 * Host implementation of the Velo `Http` native (registered under the name `Http`).
 * Same surface as the CLI's `Http` (`get` / `post` / `statusCode`), but built on
 * `HttpURLConnection` — Android has no `java.net.http.HttpClient`.
 *
 * Requires the `INTERNET` permission (declared in the manifest).
 */
class VeloHttp {

    private var lastStatusCode: Int = 200

    fun get(url: String): String = request(url, "GET", null, "")

    fun post(url: String, body: String, contentType: String): String {
        val actualContentType = if (contentType.isEmpty()) "application/json" else contentType
        return request(url, "POST", body, actualContentType)
    }

    fun statusCode(): Int = lastStatusCode

    private fun request(url: String, method: String, body: String?, contentType: String): String {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 10_000
            readTimeout = 30_000
            if (body != null) {
                doOutput = true
                setRequestProperty("Content-Type", contentType)
            }
        }
        try {
            if (body != null) {
                conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
            lastStatusCode = conn.responseCode
            val stream = if (lastStatusCode in 200..399) conn.inputStream else conn.errorStream
            return stream?.use { it.readBytes().toString(Charsets.UTF_8) } ?: ""
        } finally {
            conn.disconnect()
        }
    }
}
