package com.mojrucek.app.session

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** The outcome of asking the server to delete the signed-in account's record. */
sealed interface DeleteApiResult {
    /** 200/204: the record is gone (or never existed: deleting twice is fine). */
    data object Deleted : DeleteApiResult
    data object Unauthorized : DeleteApiResult
    data object RateLimited : DeleteApiResult
    data object ServerUnavailable : DeleteApiResult
    data object Offline : DeleteApiResult

    /** [reason] never contains the token. */
    data class Failed(val reason: String) : DeleteApiResult
}

interface AccountServer {
    suspend fun deleteMe(idToken: String): DeleteApiResult
}

/**
 * DELETE {baseUrl}/api/me with the Firebase ID token as a Bearer token and no body. Same safety rules as
 * [HttpSessionServer]: https only (unless [allowHttp], for tests), redirects are never followed, the token never
 * appears in a result.
 */
class HttpAccountServer(
    private val baseUrl: String = ServerConfig.BASE_URL,
    private val allowHttp: Boolean = false,
    private val connectTimeoutMs: Int = 10_000,
    private val readTimeoutMs: Int = 15_000,
    private val io: CoroutineDispatcher = Dispatchers.IO
) : AccountServer {

    override suspend fun deleteMe(idToken: String): DeleteApiResult = withContext(io) {
        if (!allowHttp && !baseUrl.startsWith("https://")) {
            return@withContext DeleteApiResult.Failed("the server address is not https; the sign-in token was not sent")
        }
        var connection: HttpURLConnection? = null
        try {
            connection = (URL("$baseUrl/api/me").openConnection() as HttpURLConnection).apply {
                requestMethod = "DELETE"
                connectTimeout = connectTimeoutMs
                readTimeout = readTimeoutMs
                instanceFollowRedirects = false
                setRequestProperty("Authorization", "Bearer $idToken")
                setRequestProperty("Accept", "application/json")
            }
            when (val code = connection.responseCode) {
                200, 204 -> DeleteApiResult.Deleted
                401 -> DeleteApiResult.Unauthorized
                429 -> DeleteApiResult.RateLimited
                in 500..599 -> DeleteApiResult.ServerUnavailable
                else -> DeleteApiResult.Failed("unexpected answer from the server (HTTP $code)")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            DeleteApiResult.Offline
        } catch (e: Exception) {
            DeleteApiResult.Failed("the call failed (${e.javaClass.simpleName})")
        } finally {
            connection?.disconnect()
        }
    }
}
