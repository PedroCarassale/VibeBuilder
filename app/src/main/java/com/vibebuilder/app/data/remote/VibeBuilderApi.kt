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

data class ApiPromptResponse(
    val promptMessageId: String,
    val projectVersionId: String,
    val versionNumber: Int,
    val status: String,
    val providerMeta: JSONObject?
)

data class ApiProjectVersion(
    val id: String? = null,
    val projectId: String? = null,
    val versionNumber: Int,
    val promptSnapshot: String,
    val status: String,
    val previewUrl: String? = null,
    val createdAt: String
)

enum class ApiPreviewTarget(val value: String) {
    CURRENT("current"),
    VERSION("version")
}

data class ApiProjectPreview(
    val projectId: String,
    val target: String,
    val versionId: String?,
    val versionNumber: Int?,
    val previewUrl: String
)

data class ApiPromptMessage(
    val id: String,
    val projectId: String,
    val versionId: String?,
    val role: String,
    val content: String,
    val createdAt: String,
    val versionNumber: Int?
)

interface SessionIdProvider {
    fun getSessionId(): String
}

data class ApiV0IntegrationStatus(
    val keyStorageAvailable: Boolean,
    val sessionKeyConfigured: Boolean,
    val sessionKeyHint: String?,
    val envKeyActive: Boolean
)

interface VibeBuilderApi {
    suspend fun getProjects(): List<ApiProject>
    suspend fun getProjectVersions(projectId: String): List<ApiProjectVersion>
    suspend fun getProjectPreview(
        projectId: String,
        target: ApiPreviewTarget = ApiPreviewTarget.CURRENT,
        versionNumber: Int? = null
    ): ApiProjectPreview
    suspend fun getProjectMessages(projectId: String): List<ApiPromptMessage>
    suspend fun createProject(title: String, description: String): String
    suspend fun sendPrompt(projectId: String, prompt: String): ApiPromptResponse

    suspend fun getV0IntegrationStatus(): ApiV0IntegrationStatus
    suspend fun saveV0ApiKey(apiKey: String)
    suspend fun deleteV0ApiKey()
    /** Prueba la key del cuerpo, o la guardada en sesión si [apiKey] es null o en blanco. */
    suspend fun testV0ApiKey(apiKey: String? = null)
}

class ApiRequestException(
    val statusCode: Int,
    val errorCode: String?,
    message: String
) : IOException(message)

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

    override suspend fun getProjectVersions(projectId: String): List<ApiProjectVersion> = withContext(Dispatchers.IO) {
        val response = request(
            method = "GET",
            path = "/projects/$projectId/versions"
        )
        val body = response.body.ifBlank { "[]" }
        val jsonArray = JSONArray(body)
        buildList {
            for (index in 0 until jsonArray.length()) {
                val item = jsonArray.getJSONObject(index)
                add(
                    ApiProjectVersion(
                        id = item.optStringOrNull("id"),
                        projectId = item.optStringOrNull("projectId"),
                        versionNumber = item.getInt("versionNumber"),
                        promptSnapshot = item.optStringOrNull("promptSnapshot")
                            ?: item.optStringOrNull("prompt")
                            ?: "",
                        status = item.getString("status"),
                        previewUrl = item.optStringOrNull("previewUrl"),
                        createdAt = item.getString("createdAt")
                    )
                )
            }
        }
    }

    override suspend fun getProjectMessages(projectId: String): List<ApiPromptMessage> = withContext(Dispatchers.IO) {
        val response = request(
            method = "GET",
            path = "/projects/$projectId/messages"
        )
        val body = response.body.ifBlank { "[]" }
        val jsonArray = JSONArray(body)
        buildList {
            for (index in 0 until jsonArray.length()) {
                val item = jsonArray.getJSONObject(index)
                add(
                    ApiPromptMessage(
                        id = item.getString("id"),
                        projectId = item.getString("projectId"),
                        versionId = item.optStringOrNull("versionId"),
                        role = item.getString("role"),
                        content = item.getString("content"),
                        createdAt = item.getString("createdAt"),
                        versionNumber = if (item.has("versionNumber") && !item.isNull("versionNumber")) {
                            item.getInt("versionNumber")
                        } else {
                            null
                        }
                    )
                )
            }
        }
    }

    override suspend fun getProjectPreview(
        projectId: String,
        target: ApiPreviewTarget,
        versionNumber: Int?
    ): ApiProjectPreview = withContext(Dispatchers.IO) {
        val query = buildString {
            append("?target=${target.value}")
            if (target == ApiPreviewTarget.VERSION) {
                require(versionNumber != null && versionNumber > 0) {
                    "versionNumber debe ser > 0 cuando target=version"
                }
                append("&versionNumber=$versionNumber")
            }
        }
        val response = request(
            method = "GET",
            path = "/projects/$projectId/preview$query"
        )
        val payload = JSONObject(response.body)
        ApiProjectPreview(
            projectId = payload.getString("projectId"),
            target = payload.getString("target"),
            versionId = payload.optStringOrNull("versionId"),
            versionNumber = payload.optIntOrNull("versionNumber"),
            previewUrl = payload.getString("previewUrl")
        )
    }

    override suspend fun sendPrompt(projectId: String, prompt: String): ApiPromptResponse = withContext(Dispatchers.IO) {
        val requestBody = JSONObject()
            .put("prompt", prompt)
            .toString()

        val response = request(
            method = "POST",
            path = "/projects/$projectId/prompts",
            body = requestBody
        )
        val payload = JSONObject(response.body)
        ApiPromptResponse(
            promptMessageId = payload.getString("promptMessageId"),
            projectVersionId = payload.getString("projectVersionId"),
            versionNumber = payload.getInt("versionNumber"),
            status = payload.getString("status"),
            providerMeta = payload.optJSONObject("providerMeta")
        )
    }

    override suspend fun getV0IntegrationStatus(): ApiV0IntegrationStatus = withContext(Dispatchers.IO) {
        val response = request(method = "GET", path = "/integrations/v0")
        val o = JSONObject(response.body)
        ApiV0IntegrationStatus(
            keyStorageAvailable = o.optBoolean("keyStorageAvailable", false),
            sessionKeyConfigured = o.optBoolean("sessionKeyConfigured", false),
            sessionKeyHint = o.optStringOrNull("sessionKeyHint"),
            envKeyActive = o.optBoolean("envKeyActive", false)
        )
    }

    override suspend fun saveV0ApiKey(apiKey: String) = withContext(Dispatchers.IO) {
        val requestBody = JSONObject().put("apiKey", apiKey).toString()
        request(
            method = "PUT",
            path = "/integrations/v0",
            body = requestBody
        )
        Unit
    }

    override suspend fun deleteV0ApiKey() = withContext(Dispatchers.IO) {
        request(method = "DELETE", path = "/integrations/v0")
        Unit
    }

    override suspend fun testV0ApiKey(apiKey: String?) = withContext(Dispatchers.IO) {
        val trimmed = apiKey?.trim().orEmpty()
        val body = if (trimmed.isNotEmpty()) {
            JSONObject().put("apiKey", trimmed).toString()
        } else {
            "{}"
        }
        request(method = "POST", path = "/integrations/v0/test", body = body)
        Unit
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
                throw parseError(responseBody, statusCode)
            }
            HttpResponse(statusCode = statusCode, body = responseBody)
        } finally {
            connection.disconnect()
        }
    }

    private fun parseError(responseBody: String, statusCode: Int): ApiRequestException {
        if (responseBody.isBlank()) return ApiRequestException(
            statusCode = statusCode,
            errorCode = null,
            message = "Error de red ($statusCode)"
        )
        val payload = runCatching {
            val error = JSONObject(responseBody).optJSONObject("error")
            val code = error?.optString("code")?.takeIf { it.isNotBlank() }
            val message = error?.optString("message")?.takeIf { it.isNotBlank() }
                ?: "Error de red ($statusCode)"
            code to message
        }.getOrNull()
        return ApiRequestException(
            statusCode = statusCode,
            errorCode = payload?.first,
            message = payload?.second ?: "Error de red ($statusCode)"
        )
    }

    private data class HttpResponse(
        val statusCode: Int,
        val body: String
    )
}

private fun JSONObject.optStringOrNull(key: String): String? =
    if (!has(key) || isNull(key)) null else optString(key)

private fun JSONObject.optIntOrNull(key: String): Int? =
    if (!has(key) || isNull(key)) null else getInt(key)
