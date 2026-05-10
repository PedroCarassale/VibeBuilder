package com.vibebuilder.app.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

data class ApiProject(
    val id: String,
    val title: String,
    val description: String?,
    val currentVersionId: String?,
    val createdAt: String,
    val updatedAt: String
)

interface SessionIdProvider {
    fun getSessionId(): String
}

interface VibeBuilderApi {
    suspend fun getProjects(): List<ApiProject>
    suspend fun createProject(title: String, description: String): String
}

class HttpVibeBuilderApi(
    private val baseUrl: String,
    private val sessionIdProvider: SessionIdProvider
) : VibeBuilderApi {

    override suspend fun getProjects(): List<ApiProject> = withContext(Dispatchers.IO) {
        val response = request(
            method = "GET",
            path = "/projects"
        )
        val body = response.body.ifBlank { "[]" }
        val jsonArray = JSONArray(body)
        buildList {
            for (index in 0 until jsonArray.length()) {
                val item = jsonArray.getJSONObject(index)
                add(
                    ApiProject(
                        id = item.getString("id"),
                        title = item.getString("title"),
                        description = item.optStringOrNull("description"),
                        currentVersionId = item.optStringOrNull("currentVersionId"),
                        createdAt = item.getString("createdAt"),
                        updatedAt = item.getString("updatedAt")
                    )
                )
            }
        }
    }

    override suspend fun createProject(title: String, description: String): String = withContext(Dispatchers.IO) {
        val requestBody = JSONObject()
            .put("title", title)
            .put("description", description)
            .toString()

        val response = request(
            method = "POST",
            path = "/projects",
            body = requestBody
        )
        val payload = JSONObject(response.body)
        payload.getString("projectId")
    }

    private fun request(
        method: String,
        path: String,
        body: String? = null
    ): HttpResponse {
        val connection = (URL("${baseUrl.trimEnd('/')}$path").openConnection() as HttpURLConnection).apply {
            requestMethod = method
            setRequestProperty("Accept", "application/json")
            setRequestProperty("X-Session-Id", sessionIdProvider.getSessionId())
            if (method == "POST" || method == "PUT" || method == "PATCH") {
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setRequestProperty("X-Idempotency-Key", UUID.randomUUID().toString())
            }
            doInput = true
            if (body != null) {
                doOutput = true
                outputStream.use { stream ->
                    stream.write(body.toByteArray(Charsets.UTF_8))
                }
            }
        }

        return try {
            val statusCode = connection.responseCode
            val stream = if (statusCode in 200..299) connection.inputStream else connection.errorStream
            val responseBody = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (statusCode !in 200..299) {
                throw IOException(parseErrorMessage(responseBody, statusCode))
            }
            HttpResponse(statusCode = statusCode, body = responseBody)
        } finally {
            connection.disconnect()
        }
    }

    private fun parseErrorMessage(responseBody: String, statusCode: Int): String {
        if (responseBody.isBlank()) {
            return "Error de red ($statusCode)"
        }
        return runCatching {
            val error = JSONObject(responseBody).optJSONObject("error")
            error?.optString("message")?.takeIf { it.isNotBlank() }
        }.getOrNull() ?: "Error de red ($statusCode)"
    }

    private data class HttpResponse(
        val statusCode: Int,
        val body: String
    )
}

private fun JSONObject.optStringOrNull(key: String): String? =
    if (!has(key) || isNull(key)) null else optString(key, null)
