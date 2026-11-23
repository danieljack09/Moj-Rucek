package com.mojrucek.app.session

import java.io.IOException
import java.io.InputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.concurrent.thread

/**
 * A tiny real HTTP server on this computer (plain sockets), so every kind of server behaviour can be produced on demand:
 * any status, any body, redirects, slow answers, a server that is gone. Test helper only.
 */
class FakeServer {
    class Recorded(val method: String, val path: String, val headers: Map<String, String>, val body: ByteArray)

    class Reply(val code: Int, val body: String = "", val headers: Map<String, String> = emptyMap(), val delayMs: Long = 0)

    val requests = CopyOnWriteArrayList<Recorded>()

    @Volatile var handler: (Recorded) -> Reply = { Reply(200, GOOD) }

    private val socket = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))
    val baseUrl: String get() = "http://127.0.0.1:${socket.localPort}"

    init {
        thread(isDaemon = true) {
            while (!socket.isClosed) {
                val client = try { socket.accept() } catch (e: IOException) { break }
                thread(isDaemon = true) { serve(client) }
            }
        }
    }

    fun stop() { try { socket.close() } catch (e: IOException) { } }

    private fun serve(client: Socket) {
        client.use { c ->
            try {
                val input = c.getInputStream()
                val requestLine = input.readHeaderLine() ?: return
                val (method, target) = requestLine.split(" ").let { it[0] to it[1] }
                val headers = LinkedHashMap<String, String>()
                while (true) {
                    val line = input.readHeaderLine() ?: break
                    if (line.isEmpty()) break
                    val i = line.indexOf(':')
                    if (i > 0) headers[line.substring(0, i).trim().lowercase()] = line.substring(i + 1).trim()
                }
                val length = headers["content-length"]?.toIntOrNull() ?: 0
                val body = ByteArray(length).also { var read = 0; while (read < length) { val n = input.read(it, read, length - read); if (n < 0) break; read += n } }
                val recorded = Recorded(method, target.substringBefore('?'), headers, body)
                requests += recorded

                val reply = handler(recorded)
                if (reply.delayMs > 0) Thread.sleep(reply.delayMs)
                val bytes = reply.body.toByteArray()
                val head = StringBuilder("HTTP/1.1 ${reply.code} Test\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n")
                reply.headers.forEach { (k, v) -> head.append("$k: $v\r\n") }
                head.append("\r\n")
                c.getOutputStream().apply { write(head.toString().toByteArray()); write(bytes); flush() }
            } catch (e: IOException) {
                // the client gave up (for example a timeout): fine
            }
        }
    }

    private fun InputStream.readHeaderLine(): String? {
        val sb = StringBuilder()
        while (true) {
            val b = read()
            if (b < 0) return if (sb.isEmpty()) null else sb.toString()
            if (b == '\n'.code) return sb.toString()
            if (b != '\r'.code) sb.append(b.toChar())
        }
    }

    companion object {
        const val GOOD = """{"userId":"usr_abc","kind":"GUEST","plan":"GUEST","createdAt":1000,"guestStartedAt":1000,"guestExpiresAt":1209601000,"serverTime":1000}"""
    }
}
