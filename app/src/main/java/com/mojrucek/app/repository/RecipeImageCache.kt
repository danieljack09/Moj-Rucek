package com.mojrucek.app.repository

import android.graphics.BitmapFactory
import com.mojrucek.app.session.ServerConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap

/**
 * Keeps the pictures of the server's recipes on the phone, so they show offline after the first time. Only addresses of OUR
 * server's picture service are ever fetched (`<server>/img/<recipe>-<16 hex>.<jpg|png|webp>`): the file name is derived from
 * such an address and nothing else, so no other host, path or name (not even "../") can be reached. The address changes whenever
 * the picture does, so a cached file never goes stale. Fetching never follows redirects, only reads up to [MAX_BYTES], and keeps
 * a file only if it really decodes as a picture ([validate]).
 */
class RecipeImageCache(
    private val dir: File,
    private val baseUrl: String = ServerConfig.BASE_URL,
    private val allowHttp: Boolean = false,
    private val connectTimeoutMs: Int = 10_000,
    private val readTimeoutMs: Int = 20_000,
    private val validate: (File) -> Boolean = ::decodesAsPicture
) {
    private val locks = ConcurrentHashMap<String, Mutex>()

    /** The cache file name for [url] ("12-0123456789abcdef.jpg"), or null when [url] is not one of our picture addresses. */
    fun fileName(url: String): String? {
        if (!allowHttp && !baseUrl.startsWith("https://")) return null
        val prefix = "$baseUrl/img/"
        if (!url.startsWith(prefix)) return null
        val name = url.removePrefix(prefix)
        return name.takeIf { NAME.matches(it) }
    }

    /** True for an address this cache may fetch. */
    fun isOurs(url: String): Boolean = fileName(url) != null

    /** The file when it is already on the phone, else null. Never touches the network. */
    fun cached(url: String): File? = fileName(url)?.let { File(dir, it) }?.takeIf { it.isFile && it.length() > 0 }

    /** The picture file for [url]: the cached one, or a fresh download. Null when it cannot be had (offline, refused, not a picture). */
    suspend fun fetch(url: String): File? {
        val name = fileName(url) ?: return null
        cached(url)?.let { return it }
        return locks.getOrPut(name) { Mutex() }.withLock {
            cached(url) ?: download(url, name)
        }
    }

    /** Fetches every address in [urls] that is not cached yet (one after the other); failures are skipped. Returns how many were fetched now. */
    suspend fun prefetch(urls: Collection<String>): Int {
        var fetched = 0
        for (url in urls.distinct()) {
            if (cached(url) == null && fetch(url) != null) fetched++
        }
        trim()
        return fetched
    }

    /** Keeps only the newest [keep] picture files, so replaced and removed pictures do not pile up. */
    fun trim(keep: Int = KEEP) {
        val files = dir.listFiles { f -> f.isFile && NAME.matches(f.name) }?.sortedByDescending { it.lastModified() } ?: return
        files.drop(keep).forEach { it.delete() }
    }

    private suspend fun download(url: String, name: String): File? = withContext(Dispatchers.IO) {
        var connection: HttpURLConnection? = null
        val tmp = File(dir, ".$name.part")
        try {
            dir.mkdirs()
            connection = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = connectTimeoutMs
                readTimeout = readTimeoutMs
                instanceFollowRedirects = false
                setRequestProperty("Accept", "image/*")
            }
            if (connection.responseCode != 200) return@withContext null
            if (connection.contentLengthLong > MAX_BYTES) return@withContext null
            val out = ByteArrayOutputStream()
            connection.inputStream.use { input ->
                val buffer = ByteArray(16 * 1024)
                while (true) {
                    val n = input.read(buffer)
                    if (n < 0) break
                    out.write(buffer, 0, n)
                    if (out.size() > MAX_BYTES) return@withContext null
                }
            }
            if (out.size() == 0) return@withContext null
            tmp.writeBytes(out.toByteArray())
            if (!validate(tmp)) return@withContext null
            val target = File(dir, name)
            if (!tmp.renameTo(target)) return@withContext null
            target
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            null
        } catch (e: Exception) {
            null
        } finally {
            tmp.delete()
            connection?.disconnect()
        }
    }

    companion object {
        const val MAX_BYTES = 2 * 1024 * 1024
        const val KEEP = 300
        private val NAME = Regex("^[0-9]{1,18}-[0-9a-f]{16}\\.(jpg|png|webp)$")
    }
}

/** True when the file decodes as a picture (a quick header check, not a full decode). */
fun decodesAsPicture(file: File): Boolean = try {
    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.path, options)
    options.outWidth > 0 && options.outHeight > 0
} catch (e: Exception) {
    false
}

/** One cache for the whole app (screens and the catalog sync share it, so a picture is fetched only once). */
object RecipeImageCacheHolder {
    @Volatile
    private var instance: RecipeImageCache? = null

    fun get(context: android.content.Context): RecipeImageCache =
        instance ?: synchronized(this) {
            instance ?: RecipeImageCache(File(context.applicationContext.filesDir, "recipe-images")).also { instance = it }
        }
}

/**
 * The framing point of a server picture travels inside the picture address as "#focus=x,y" (so the phone database needs no new
 * column). The part after "#" is never sent to the server and never part of a cache file name.
 */
object PictureFocus {
    data class Point(val x: Float, val y: Float)

    val MIDDLE = Point(0.5f, 0.5f)

    /** [url] with the point attached; the middle (or no/invalid point) leaves the address as it is. */
    fun attach(url: String, x: Float?, y: Float?): String {
        if (x == null || y == null || !x.isFinite() || !y.isFinite() || x !in 0f..1f || y !in 0f..1f) return url
        if (x == 0.5f && y == 0.5f) return url
        return "$url#focus=$x,$y"
    }

    /** The address without any "#..." part. */
    fun strip(uri: String): String = uri.substringBefore('#')

    /** The point attached to [uri], or the middle when there is none or it is malformed. */
    fun of(uri: String?): Point {
        val part = uri?.substringAfter('#', "")?.takeIf { it.startsWith("focus=") }?.removePrefix("focus=") ?: return MIDDLE
        val nums = part.split(',')
        if (nums.size != 2) return MIDDLE
        val x = nums[0].toFloatOrNull()
        val y = nums[1].toFloatOrNull()
        if (x == null || y == null || !x.isFinite() || !y.isFinite() || x !in 0f..1f || y !in 0f..1f) return MIDDLE
        return Point(x, y)
    }
}
