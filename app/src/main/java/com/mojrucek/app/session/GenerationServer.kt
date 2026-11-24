package com.mojrucek.app.session

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

/** The full details of one recipe, as the server sends them with an approved list. */
@Serializable
data class ServerRecipeDetails(
    val id: Long,
    val description: String? = null,
    val instructionSteps: List<String> = emptyList(),
    val notes: String? = null,
    val tags: List<String> = emptyList()
)

/** The server's answer to an approved list: what is unlocked, until when, and when the next list is possible. */
@Serializable
data class ServerGeneration(
    val generationId: Long,
    val createdAt: Long,
    val unlockedUntil: Long? = null,
    val nextAllowedAt: Long? = null,
    val recipes: List<ServerRecipeDetails> = emptyList(),
    val serverTime: Long
)

/** Where the user stands (GET /api/unlocks). */
@Serializable
data class ServerLimitState(
    val plan: String,
    val guestExpired: Boolean = false,
    val canGenerateNow: Boolean = true,
    val nextAllowedAt: Long? = null,
    val unlockedUntil: Long? = null,
    val recipes: List<ServerRecipeDetails> = emptyList(),
    val serverTime: Long
)

/** The outcome of one call to the list endpoints. None of these is shown to the user as a raw error. */
sealed interface GenerationApiResult<out T> {
    data class Ok<T>(val value: T, val deviceNowAtResponseMs: Long) : GenerationApiResult<T>

    /** The server understood but said no (a rule): [code] is one of TOO_SOON, GUEST_EXPIRED, TOO_MANY_RECIPES, ... */
    data class Refused(val code: String, val nextAllowedAt: Long?, val max: Int?, val serverTime: Long?) : GenerationApiResult<Nothing>

    data object Unauthorized : GenerationApiResult<Nothing>
    data object RateLimited : GenerationApiResult<Nothing>
    data object ServerUnavailable : GenerationApiResult<Nothing>
    data object Offline : GenerationApiResult<Nothing>

    /** [reason] never contains the token. */
    data class Failed(val reason: String) : GenerationApiResult<Nothing>
}

interface GenerationServer {
    suspend fun generate(idToken: String, recipeIds: List<Long>, requestId: String): GenerationApiResult<ServerGeneration>
    suspend fun state(idToken: String): GenerationApiResult<ServerLimitState>
}

@Serializable
private data class GenerateBody(val recipeIds: List<Long>, val requestId: String)

@Serializable
private data class ErrorBody(
    val error: String? = null,
    val code: String? = null,
    val nextAllowedAt: Long? = null,
    val max: Int? = null,
    val serverTime: Long? = null
)

/**
 * `POST {baseUrl}/api/generations` and `GET {baseUrl}/api/unlocks` with the Firebase ID token as a Bearer token.
 * Same safety rules as the other server calls: https only (unless [allowHttp], for tests), redirects are never
 * followed, answers are size-limited and strictly read, the token never appears in a result.
 */
class HttpGenerationServer(
    private val baseUrl: String = ServerConfig.BASE_URL,
    private val allowHttp: Boolean = false,
    private val connectTimeoutMs: Int = 10_000,
    private val readTimeoutMs: Int = 20_000,
    private val io: CoroutineDispatcher = Dispatchers.IO
) : GenerationServer {

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun generate(idToken: String, recipeIds: List<Long>, requestId: String): GenerationApiResult<ServerGeneration> =
        call("POST", "/api/generations", idToken, json.encodeToString(GenerateBody.serializer(), GenerateBody(recipeIds, requestId))) {
            json.decodeFromString(ServerGeneration.serializer(), it)
        }

    override suspend fun state(idToken: String): GenerationApiResult<ServerLimitState> =
        call("GET", "/api/unlocks", idToken, null) { json.decodeFromString(ServerLimitState.serializer(), it) }

    private suspend fun <T> call(
        method: String, path: String, idToken: String, body: String?, parse: (String) -> T
    ): GenerationApiResult<T> = withContext(io) {
        if (!allowHttp && !baseUrl.startsWith("https://")) {
            return@withContext GenerationApiResult.Failed("the server address is not https; the sign-in token was not sent")
        }
        var connection: HttpURLConnection? = null
        try {
            val before = System.currentTimeMillis()
            connection = (URL("$baseUrl$path").openConnection() as HttpURLConnection).apply {
                requestMethod = method
                connectTimeout = connectTimeoutMs
                readTimeout = readTimeoutMs
                instanceFollowRedirects = false
                setRequestProperty("Authorization", "Bearer $idToken")
                setRequestProperty("Accept", "application/json")
                if (body != null) {
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    val bytes = body.toByteArray(Charsets.UTF_8)
                    setFixedLengthStreamingMode(bytes.size)
                    outputStream.use { it.write(bytes) }
                }
            }
            val code = connection.responseCode
            val after = System.currentTimeMillis()
            when {
                code == 200 -> {
                    val text = readLimited(connection.inputStream) ?: return@withContext GenerationApiResult.Failed("the answer was too large")
                    try {
                        GenerationApiResult.Ok(parse(text), before + (after - before) / 2)
                    } catch (e: SerializationException) {
                        GenerationApiResult.Failed("the answer could not be read")
                    } catch (e: IllegalArgumentException) {
                        GenerationApiResult.Failed("the answer could not be read")
                    }
                }
                code == 401 -> GenerationApiResult.Unauthorized
                code == 429 -> GenerationApiResult.RateLimited
                code in 500..599 -> GenerationApiResult.ServerUnavailable
                code in 400..499 -> refusal(connection, code)
                else -> GenerationApiResult.Failed("unexpected answer from the server (HTTP $code)")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            GenerationApiResult.Offline
        } catch (e: Exception) {
            GenerationApiResult.Failed("the call failed (${e.javaClass.simpleName})")
        } finally {
            connection?.disconnect()
        }
    }

    /** 4xx with a machine-readable code (403/409/400): a rule said no. Anything unreadable is a plain failure. */
    private fun refusal(connection: HttpURLConnection, status: Int): GenerationApiResult<Nothing> {
        val text = connection.errorStream?.let { readLimited(it) }
        val parsed = try { text?.let { json.decodeFromString(ErrorBody.serializer(), it) } } catch (e: Exception) { null }
        val code = parsed?.code ?: return GenerationApiResult.Failed("the server refused (HTTP $status)")
        return GenerationApiResult.Refused(code, parsed.nextAllowedAt, parsed.max, parsed.serverTime)
    }

    private fun readLimited(stream: InputStream): String? {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        stream.use { input ->
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                out.write(buffer, 0, n)
                if (out.size() > MAX_BODY_BYTES) return null
            }
        }
        return out.toString(Charsets.UTF_8.name())
    }

    private companion object {
        const val MAX_BODY_BYTES = 4 * 1024 * 1024
    }
}
