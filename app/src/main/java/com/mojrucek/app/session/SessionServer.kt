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

/** What the server says about the signed-in user (`POST /api/session`). Dates are ms since 1970. */
@Serializable
data class ServerSession(
    val userId: String,
    /** GUEST or REGISTERED today; other values may appear in future and must not break the app. */
    val kind: String,
    val plan: String,
    val createdAt: Long? = null,
    val guestStartedAt: Long? = null,
    val guestExpiresAt: Long? = null,
    val serverTime: Long
)

/** The outcome of one call to the server. None of these is shown to the user as an error. */
sealed interface SessionApiResult {
    /** [deviceNowAtResponseMs] is the phone's clock at about the moment the server answered (for the clock difference). */
    data class Ok(val session: ServerSession, val deviceNowAtResponseMs: Long) : SessionApiResult

    /** 401: the server did not accept the sign-in token. */
    data object Unauthorized : SessionApiResult

    /** 429: too many requests. */
    data object RateLimited : SessionApiResult

    /** 503 or any 5xx: the server (or Google's keys it checks against) is not ready. */
    data object ServerUnavailable : SessionApiResult

    /** The server could not be reached at all (no internet, timeout, DNS, TLS). */
    data object Offline : SessionApiResult

    /** Anything else: an unexpected status, an unreadable answer, or a refusal to send. [reason] never contains the token. */
    data class Failed(val reason: String) : SessionApiResult
}

/** Opens (or refreshes) the user's session on the server. */
interface SessionServer {
    suspend fun open(idToken: String, deviceNowMs: () -> Long = System::currentTimeMillis): SessionApiResult
}

/**
 * The real call: `POST {baseUrl}/api/session` with the Firebase ID token as a Bearer token and nothing else.
 *
 * Safety rules:
 *  - the token is only ever sent over https ([allowHttp] exists for tests against a local fake server);
 *  - redirects are NOT followed, so a redirect can never carry the token to another address. Two independent
 *    defences: following is switched off explicitly, and the empty POST body is sent in fixed-length mode, which
 *    also stops the JDK from re-sending a POST to a new address. A test fails only if BOTH are removed;
 *  - the answer is size-limited and strictly checked; the token is never put into a result or an error text.
 */
class HttpSessionServer(
    private val baseUrl: String = ServerConfig.BASE_URL,
    private val allowHttp: Boolean = false,
    private val connectTimeoutMs: Int = 10_000,
    private val readTimeoutMs: Int = 15_000,
    private val io: CoroutineDispatcher = Dispatchers.IO
) : SessionServer {

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun open(idToken: String, deviceNowMs: () -> Long): SessionApiResult = withContext(io) {
        if (!allowHttp && !baseUrl.startsWith("https://")) {
            return@withContext SessionApiResult.Failed("the server address is not https; the sign-in token was not sent")
        }
        var connection: HttpURLConnection? = null
        try {
            val before = deviceNowMs()
            connection = (URL("$baseUrl/api/session").openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = connectTimeoutMs
                readTimeout = readTimeoutMs
                instanceFollowRedirects = false
                doOutput = true
                setFixedLengthStreamingMode(0)
                setRequestProperty("Authorization", "Bearer $idToken")
                setRequestProperty("Accept", "application/json")
            }
            val code = connection.responseCode
            val after = deviceNowMs()
            when {
                code == 200 -> parse(readLimited(connection.inputStream), deviceNowAtResponse = before + (after - before) / 2)
                code == 401 -> SessionApiResult.Unauthorized
                code == 429 -> SessionApiResult.RateLimited
                code in 500..599 -> SessionApiResult.ServerUnavailable
                else -> SessionApiResult.Failed("unexpected answer from the server (HTTP $code)")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            SessionApiResult.Offline
        } catch (e: Exception) {
            SessionApiResult.Failed("the call failed (${e.javaClass.simpleName})")
        } finally {
            connection?.disconnect()
        }
    }

    private fun parse(body: String?, deviceNowAtResponse: Long): SessionApiResult {
        if (body == null) return SessionApiResult.Failed("the server's answer was too large")
        val session = try {
            json.decodeFromString(ServerSession.serializer(), body)
        } catch (e: SerializationException) {
            return SessionApiResult.Failed("the server's answer could not be read")
        } catch (e: IllegalArgumentException) {
            return SessionApiResult.Failed("the server's answer could not be read")
        }
        if (session.userId.isBlank() || session.serverTime <= 0) return SessionApiResult.Failed("the server's answer is incomplete")
        return SessionApiResult.Ok(session, deviceNowAtResponse)
    }

    /** Reads at most [MAX_BODY_BYTES]; null when the answer is bigger (never read without a limit). */
    private fun readLimited(stream: InputStream): String? {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(4096)
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
        const val MAX_BODY_BYTES = 64 * 1024
    }
}
