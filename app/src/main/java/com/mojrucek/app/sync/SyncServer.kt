package com.mojrucek.app.sync

import com.mojrucek.app.session.ServerConfig
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

/** What exists on the server for the account (no data). Revision 0 = nothing stored. */
@Serializable
data class SyncMeta(val revision: Int, val updatedAt: Long? = null, val size: Long = 0, val serverTime: Long = 0)

/** The stored snapshot. [data] is the exact text that was uploaded. */
@Serializable
data class SyncData(val revision: Int, val updatedAt: Long? = null, val size: Long = 0, val data: String, val serverTime: Long = 0)

@Serializable
private data class WriteResult(val revision: Int, val updatedAt: Long? = null, val size: Long = 0, val serverTime: Long = 0)

@Serializable
private data class PutBody(val baseRevision: Int, val data: String)

@Serializable
private data class ErrorBody(val code: String? = null, val revision: Int? = null)

sealed interface SyncApiResult<out T> {
    data class Ok<T>(val value: T) : SyncApiResult<T>

    /** 409: another phone uploaded first. [serverRevision] is the current one. */
    data class Conflict(val serverRevision: Int) : SyncApiResult<Nothing>

    /** 404 NO_SNAPSHOT: nothing stored yet. */
    data object NothingStored : SyncApiResult<Nothing>

    /** 413: the snapshot is bigger than the server accepts. */
    data object TooLarge : SyncApiResult<Nothing>

    data object Unauthorized : SyncApiResult<Nothing>
    data object RateLimited : SyncApiResult<Nothing>
    data object ServerUnavailable : SyncApiResult<Nothing>
    data object Offline : SyncApiResult<Nothing>

    /** [reason] never contains the token or any of the data. */
    data class Failed(val reason: String) : SyncApiResult<Nothing>
}

interface SyncServer {
    suspend fun meta(idToken: String): SyncApiResult<SyncMeta>
    suspend fun data(idToken: String): SyncApiResult<SyncData>
    suspend fun put(idToken: String, baseRevision: Int, data: String): SyncApiResult<Int>
}

/**
 * `GET /api/sync`, `GET /api/sync/data` and `PUT /api/sync` with the Firebase ID token as a Bearer token. Same safety rules
 * as the other server calls: https only (unless [allowHttp], for tests), redirects are never followed, answers are
 * size-limited and strictly read, neither the token nor the data ever appears in a result.
 */
class HttpSyncServer(
    private val baseUrl: String = ServerConfig.BASE_URL,
    private val allowHttp: Boolean = false,
    private val connectTimeoutMs: Int = 10_000,
    private val readTimeoutMs: Int = 30_000,
    private val io: CoroutineDispatcher = Dispatchers.IO
) : SyncServer {

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun meta(idToken: String): SyncApiResult<SyncMeta> =
        call("GET", "/api/sync", idToken, null) { json.decodeFromString(SyncMeta.serializer(), it) }

    override suspend fun data(idToken: String): SyncApiResult<SyncData> =
        call("GET", "/api/sync/data", idToken, null) { json.decodeFromString(SyncData.serializer(), it) }

    override suspend fun put(idToken: String, baseRevision: Int, data: String): SyncApiResult<Int> =
        call("PUT", "/api/sync", idToken, json.encodeToString(PutBody.serializer(), PutBody(baseRevision, data))) {
            json.decodeFromString(WriteResult.serializer(), it).revision
        }

    private suspend fun <T> call(method: String, path: String, idToken: String, body: String?, parse: (String) -> T): SyncApiResult<T> =
        withContext(io) {
            if (!allowHttp && !baseUrl.startsWith("https://")) {
                return@withContext SyncApiResult.Failed("the server address is not https; the sign-in token was not sent")
            }
            var connection: HttpURLConnection? = null
            try {
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
                when (val code = connection.responseCode) {
                    200 -> {
                        val text = readLimited(connection.inputStream) ?: return@withContext SyncApiResult.Failed("the answer was too large")
                        try {
                            SyncApiResult.Ok(parse(text))
                        } catch (e: SerializationException) {
                            SyncApiResult.Failed("the answer could not be read")
                        } catch (e: IllegalArgumentException) {
                            SyncApiResult.Failed("the answer could not be read")
                        }
                    }
                    401 -> SyncApiResult.Unauthorized
                    413 -> SyncApiResult.TooLarge
                    429 -> SyncApiResult.RateLimited
                    in 500..599 -> SyncApiResult.ServerUnavailable
                    404, 409 -> {
                        val err = connection.errorStream?.let { readLimited(it) }?.let {
                            try { json.decodeFromString(ErrorBody.serializer(), it) } catch (e: Exception) { null }
                        }
                        when {
                            code == 409 && err?.code == "SYNC_CONFLICT" && err.revision != null -> SyncApiResult.Conflict(err.revision)
                            code == 404 && err?.code == "NO_SNAPSHOT" -> SyncApiResult.NothingStored
                            else -> SyncApiResult.Failed("unexpected answer from the server (HTTP $code)")
                        }
                    }
                    else -> SyncApiResult.Failed("unexpected answer from the server (HTTP $code)")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: IOException) {
                SyncApiResult.Offline
            } catch (e: Exception) {
                SyncApiResult.Failed("the call failed (${e.javaClass.simpleName})")
            } finally {
                connection?.disconnect()
            }
        }

    private fun readLimited(stream: InputStream): String? {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(16 * 1024)
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
        /** A 2 MB snapshot is at most about 6 MB once the text is escaped inside JSON. */
        const val MAX_BODY_BYTES = 8 * 1024 * 1024
    }
}
