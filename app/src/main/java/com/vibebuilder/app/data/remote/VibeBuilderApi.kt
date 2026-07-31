package com.vibebuilder.app.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.vibebuilder.app.data.auth.AuthSession
import com.vibebuilder.app.data.auth.AuthUser
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
    val currentVersionNumber: Int? = null,
    val visibility: String = "private",
    val publishedAt: String? = null,
    val originalProjectId: String? = null,
    val originalProjectTitle: String? = null,
    val originalAuthorName: String? = null,
    val forkedAt: String? = null,
    val createdAt: String,
    val updatedAt: String
)

data class ApiPublicProject(
    val id: String,
    val title: String,
    val description: String?,
    val ownerName: String,
    val currentVersionNumber: Int?,
    val currentPreviewUrl: String?,
    val forkCount: Int,
    val publishedAt: String?,
    val updatedAt: String,
    val createdAt: String,
    val originalProjectId: String?,
    val originalProjectTitle: String?,
    val originalAuthorName: String?,
    val versions: List<ApiProjectVersion> = emptyList()
)

data class ApiForkResponse(
    val projectId: String,
    val originalProjectId: String?,
    val originalProjectTitle: String?,
    val originalAuthorName: String?
)

data class ApiPromptResponse(
    val promptMessageId: String,
    val projectVersionId: String,
    val versionNumber: Int,
    val status: String,
    val providerMeta: JSONObject?,
    val sourceVersionId: String? = null,
    val attemptNumber: Int = 1,
    val failureCode: String? = null
)

data class ApiVersionArtifact(
    val framework: String,
    val fileCount: Int,
    val totalBytes: Long,
    val validationStatus: String,
    val hasExport: Boolean
)

data class ApiProjectVersion(
    val id: String? = null,
    val projectId: String? = null,
    val versionNumber: Int,
    val promptSnapshot: String,
    val status: String,
    val previewUrl: String? = null,
    val createdAt: String,
    val sourceVersionId: String? = null,
    val attemptNumber: Int = 1,
    val failureCode: String? = null,
    val startedAt: String? = null,
    val completedAt: String? = null,
    val artifact: ApiVersionArtifact? = null
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

fun interface AuthTokenProvider {
    fun getAuthToken(): String?
}

data class ApiV0IntegrationStatus(
    val keyStorageAvailable: Boolean,
    val sessionKeyConfigured: Boolean,
    val sessionKeyHint: String?,
    val envKeyActive: Boolean
)

interface VibeBuilderApi {
    suspend fun register(name: String, email: String, password: String): AuthSession
    suspend fun login(email: String, password: String): AuthSession
    suspend fun getCurrentUser(): AuthUser
    suspend fun logout()

    suspend fun getProjects(): List<ApiProject>
    suspend fun getLibraryProjects(): List<ApiPublicProject>
    suspend fun getLibraryProject(projectId: String): ApiPublicProject
    suspend fun getProjectVersions(projectId: String): List<ApiProjectVersion>
    suspend fun getProjectPreview(
        projectId: String,
        target: ApiPreviewTarget = ApiPreviewTarget.CURRENT,
        versionNumber: Int? = null
    ): ApiProjectPreview
    suspend fun getProjectMessages(projectId: String): List<ApiPromptMessage>
    suspend fun createProject(title: String, description: String): String
    suspend fun updateProject(projectId: String, title: String, description: String): ApiProject
    suspend fun updateProjectVisibility(projectId: String, visibility: String): ApiProject
    suspend fun deleteProject(projectId: String)
    suspend fun forkProject(projectId: String): ApiForkResponse
    suspend fun sendPrompt(projectId: String, prompt: String): ApiPromptResponse
    suspend fun regenerateVersion(
        projectId: String,
        versionId: String,
        correctedPrompt: String? = null
    ): ApiPromptResponse

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
    private val sessionIdProvider: SessionIdProvider,
    private val authTokenProvider: AuthTokenProvider? = null
) : VibeBuilderApi {

    private companion object {
        /** v0 puede tardar varios minutos; el default de HttpURLConnection corta antes que Vercel (maxDuration 300s). */
        const val PROMPT_READ_TIMEOUT_MS = 300_000
        const val DEFAULT_CONNECT_TIMEOUT_MS = 30_000
        const val DEFAULT_READ_TIMEOUT_MS = 60_000
    }

    override suspend fun register(name: String, email: String, password: String): AuthSession = withContext(Dispatchers.IO) {
        val response = request(
            method = "POST",
            path = "/auth/register",
            body = JSONObject()
                .put("name", name)
                .put("email", email)
                .put("password", password)
                .toString(),
            includeAuth = false
        )
        JSONObject(response.body).toAuthSession()
    }

    override suspend fun login(email: String, password: String): AuthSession = withContext(Dispatchers.IO) {
        val response = request(
            method = "POST",
            path = "/auth/login",
            body = JSONObject()
                .put("email", email)
                .put("password", password)
                .toString(),
            includeAuth = false
        )
        JSONObject(response.body).toAuthSession()
    }

    override suspend fun getCurrentUser(): AuthUser = withContext(Dispatchers.IO) {
        val response = request(method = "GET", path = "/auth/me")
        JSONObject(response.body).getJSONObject("user").toAuthUser()
    }

    override suspend fun logout() = withContext(Dispatchers.IO) {
        request(method = "POST", path = "/auth/logout", body = "{}")
        Unit
    }

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
                        currentVersionNumber = item.optIntOrNull("currentVersionNumber"),
                        visibility = item.optString("visibility", "private"),
                        publishedAt = item.optStringOrNull("publishedAt"),
                        originalProjectId = item.optStringOrNull("originalProjectId"),
                        originalProjectTitle = item.optStringOrNull("originalProjectTitle"),
                        originalAuthorName = item.optStringOrNull("originalAuthorName"),
                        forkedAt = item.optStringOrNull("forkedAt"),
                        createdAt = item.getString("createdAt"),
                        updatedAt = item.getString("updatedAt")
                    )
                )
            }
        }
    }

    override suspend fun getLibraryProjects(): List<ApiPublicProject> = withContext(Dispatchers.IO) {
        val response = request(method = "GET", path = "/library/projects")
        val jsonArray = JSONArray(response.body.ifBlank { "[]" })
        buildList {
            for (index in 0 until jsonArray.length()) {
                add(jsonArray.getJSONObject(index).toApiPublicProject())
            }
        }
    }

    override suspend fun getLibraryProject(projectId: String): ApiPublicProject = withContext(Dispatchers.IO) {
        val response = request(method = "GET", path = "/library/projects/$projectId")
        JSONObject(response.body).toApiPublicProject()
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

    override suspend fun updateProject(
        projectId: String,
        title: String,
        description: String
    ): ApiProject = withContext(Dispatchers.IO) {
        val response = request(
            method = "PATCH",
            path = "/projects/$projectId",
            body = JSONObject().put("title", title).put("description", description).toString()
        )
        JSONObject(response.body).toApiProject()
    }

    override suspend fun updateProjectVisibility(projectId: String, visibility: String): ApiProject = withContext(Dispatchers.IO) {
        val response = request(
            method = "PATCH",
            path = "/projects/$projectId",
            body = JSONObject().put("visibility", visibility).toString()
        )
        JSONObject(response.body).toApiProject()
    }

    override suspend fun deleteProject(projectId: String) = withContext(Dispatchers.IO) {
        request(method = "DELETE", path = "/projects/$projectId")
        Unit
    }

    override suspend fun forkProject(projectId: String): ApiForkResponse = withContext(Dispatchers.IO) {
        val response = request(
            method = "POST",
            path = "/projects/$projectId/fork",
            body = "{}"
        )
        val payload = JSONObject(response.body)
        ApiForkResponse(
            projectId = payload.getString("projectId"),
            originalProjectId = payload.optStringOrNull("originalProjectId"),
            originalProjectTitle = payload.optStringOrNull("originalProjectTitle"),
            originalAuthorName = payload.optStringOrNull("originalAuthorName")
        )
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
                add(jsonArray.getJSONObject(index).toApiProjectVersion())
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
            body = requestBody,
            readTimeoutMs = PROMPT_READ_TIMEOUT_MS
        )
        val payload = JSONObject(response.body)
        ApiPromptResponse(
            promptMessageId = payload.getString("promptMessageId"),
            projectVersionId = payload.getString("projectVersionId"),
            versionNumber = payload.getInt("versionNumber"),
            status = payload.getString("status"),
            providerMeta = payload.optJSONObject("providerMeta"),
            sourceVersionId = payload.optStringOrNull("sourceVersionId"),
            attemptNumber = payload.optIntOrNull("attemptNumber") ?: 1,
            failureCode = payload.optStringOrNull("failureCode")
        )
    }

    override suspend fun regenerateVersion(
        projectId: String,
        versionId: String,
        correctedPrompt: String?
    ): ApiPromptResponse = withContext(Dispatchers.IO) {
        val trimmedPrompt = correctedPrompt?.trim().orEmpty()
        val requestBody = JSONObject().apply {
            if (trimmedPrompt.isNotEmpty()) put("prompt", trimmedPrompt)
        }.toString()

        val response = request(
            method = "POST",
            path = "/projects/$projectId/versions/$versionId/regenerate",
            body = requestBody,
            readTimeoutMs = PROMPT_READ_TIMEOUT_MS
        )
        val payload = JSONObject(response.body)
        ApiPromptResponse(
            promptMessageId = payload.getString("promptMessageId"),
            projectVersionId = payload.getString("projectVersionId"),
            versionNumber = payload.getInt("versionNumber"),
            status = payload.getString("status"),
            providerMeta = payload.optJSONObject("providerMeta"),
            sourceVersionId = payload.optStringOrNull("sourceVersionId"),
            attemptNumber = payload.optIntOrNull("attemptNumber") ?: 1,
            failureCode = payload.optStringOrNull("failureCode")
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
        body: String? = null,
        connectTimeoutMs: Int = DEFAULT_CONNECT_TIMEOUT_MS,
        readTimeoutMs: Int = DEFAULT_READ_TIMEOUT_MS,
        includeAuth: Boolean = true
    ): HttpResponse {
        val connection = (URL("${baseUrl.trimEnd('/')}$path").openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = connectTimeoutMs
            readTimeout = readTimeoutMs
            setRequestProperty("Accept", "application/json")
            setRequestProperty("X-Session-Id", sessionIdProvider.getSessionId())
            if (includeAuth) {
                authTokenProvider?.getAuthToken()?.trim()?.takeIf { it.isNotEmpty() }?.let { token ->
                    setRequestProperty("Authorization", "Bearer $token")
                }
            }
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
        } catch (error: IOException) {
            throw IOException(mapNetworkFailure(error), error)
        } finally {
            connection.disconnect()
        }
    }

    private fun mapNetworkFailure(error: IOException): String {
        val raw = error.message?.trim().orEmpty()
        if (raw.contains("timeout", ignoreCase = true) || error is java.net.SocketTimeoutException) {
            return "La generación tardó demasiado y la conexión se cortó. Reintentá: si v0 ya terminó, el historial puede haberse guardado."
        }
        if (raw.equals("Internal Server Error", ignoreCase = true)) {
            return "El servidor tardó en responder (generación larga). Reintentá o abrí Historial para ver si la versión ya está guardada."
        }
        return if (raw.isNotEmpty()) raw else "Error de red al contactar el backend"
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

private fun JSONObject.toApiProject() = ApiProject(
    id = getString("id"),
    title = getString("title"),
    description = optStringOrNull("description"),
    currentVersionId = optStringOrNull("currentVersionId"),
    currentVersionNumber = optIntOrNull("currentVersionNumber"),
    visibility = optString("visibility", "private"),
    publishedAt = optStringOrNull("publishedAt"),
    originalProjectId = optStringOrNull("originalProjectId"),
    originalProjectTitle = optStringOrNull("originalProjectTitle"),
    originalAuthorName = optStringOrNull("originalAuthorName"),
    forkedAt = optStringOrNull("forkedAt"),
    createdAt = getString("createdAt"),
    updatedAt = getString("updatedAt")
)

private fun JSONObject.toApiPublicProject(): ApiPublicProject {
    val versionArray = optJSONArray("versions")
    val parsedVersions = buildList {
        if (versionArray != null) {
            for (index in 0 until versionArray.length()) {
                add(versionArray.getJSONObject(index).toApiProjectVersion())
            }
        }
    }
    return ApiPublicProject(
        id = getString("id"),
        title = getString("title"),
        description = optStringOrNull("description"),
        ownerName = optStringOrNull("ownerName") ?: "Invitado",
        currentVersionNumber = optIntOrNull("currentVersionNumber"),
        currentPreviewUrl = optStringOrNull("currentPreviewUrl"),
        forkCount = optIntOrNull("forkCount") ?: 0,
        publishedAt = optStringOrNull("publishedAt"),
        updatedAt = getString("updatedAt"),
        createdAt = getString("createdAt"),
        originalProjectId = optStringOrNull("originalProjectId"),
        originalProjectTitle = optStringOrNull("originalProjectTitle"),
        originalAuthorName = optStringOrNull("originalAuthorName"),
        versions = parsedVersions
    )
}

private fun JSONObject.toApiProjectVersion(): ApiProjectVersion {
    val artifactObject = optJSONObject("artifact")
    return ApiProjectVersion(
        id = optStringOrNull("id"),
        projectId = optStringOrNull("projectId"),
        versionNumber = getInt("versionNumber"),
        promptSnapshot = optStringOrNull("promptSnapshot")
            ?: optStringOrNull("prompt")
            ?: "",
        status = getString("status"),
        previewUrl = optStringOrNull("previewUrl"),
        createdAt = getString("createdAt"),
        sourceVersionId = optStringOrNull("sourceVersionId"),
        attemptNumber = optIntOrNull("attemptNumber") ?: 1,
        failureCode = optStringOrNull("failureCode"),
        startedAt = optStringOrNull("startedAt"),
        completedAt = optStringOrNull("completedAt"),
        artifact = artifactObject?.let { artifact ->
            ApiVersionArtifact(
                framework = artifact.getString("framework"),
                fileCount = artifact.getInt("fileCount"),
                totalBytes = artifact.getLong("totalBytes"),
                validationStatus = artifact.getString("validationStatus"),
                hasExport = artifact.optBoolean("hasExport", false)
            )
        }
    )
}

private fun JSONObject.toAuthSession(): AuthSession = AuthSession(
    token = getString("token"),
    expiresAt = getString("expiresAt"),
    user = getJSONObject("user").toAuthUser()
)

private fun JSONObject.toAuthUser(): AuthUser = AuthUser(
    id = getString("id"),
    email = optStringOrNull("email"),
    name = optStringOrNull("name"),
    avatarUrl = optStringOrNull("avatarUrl")
)

private fun JSONObject.optStringOrNull(key: String): String? =
    if (!has(key) || isNull(key)) null else optString(key)

private fun JSONObject.optIntOrNull(key: String): Int? =
    if (!has(key) || isNull(key)) null else getInt(key)
