package com.mojrucek.app.repository

import com.mojrucek.app.session.FakeServer
import com.mojrucek.app.session.FakeServer.Reply
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

class RecipeImageCacheTest {

    private lateinit var fake: FakeServer
    private lateinit var dir: File
    private val picture = ByteArray(5000) { (it % 251).toByte() }
    private val name = "12-0123456789abcdef.jpg"

    @Before fun setUp() {
        fake = FakeServer()
        dir = Files.createTempDirectory("recipe-images-test").toFile()
        fake.handler = { Reply(200, bytes = picture, headers = mapOf("Content-Type" to "image/jpeg")) }
    }

    @After fun tearDown() { fake.stop(); dir.deleteRecursively() }

    private fun cache(validate: (File) -> Boolean = { true }) =
        RecipeImageCache(dir, baseUrl = fake.baseUrl, allowHttp = true, connectTimeoutMs = 2000, readTimeoutMs = 3000, validate = validate)

    private fun url(n: String = name) = "${fake.baseUrl}/img/$n"

    // ---------- which addresses are ours ----------

    @Test fun onlyOurPictureAddressesAreRecognised() {
        val c = cache()
        assertEquals(name, c.fileName(url()))
        assertEquals("7-ffffffffffffffff.png", c.fileName(url("7-ffffffffffffffff.png")))
        assertEquals("7-0000000000000000.webp", c.fileName(url("7-0000000000000000.webp")))
        for (bad in listOf(
            "https://evil.example/img/$name",                       // another host
            "${fake.baseUrl}/other/$name", "${fake.baseUrl}/img/", "${fake.baseUrl}/img",
            url("12-0123456789abcde.jpg"), url("12-0123456789abcdeg.jpg"), url("12-0123456789ABCDEF.jpg"),     // wrong hash shape
            url("12-0123456789abcdef.svg"), url("12-0123456789abcdef.jpg.exe"), url("12-0123456789abcdef"),
            url("../12-0123456789abcdef.jpg"), url("..%2F12-0123456789abcdef.jpg"), url("sub/12-0123456789abcdef.jpg"),
            url("12-0123456789abcdef.jpg?x=1"), url("12-0123456789abcdef.jpg#x"), url("-0123456789abcdef.jpg"), url("abc-0123456789abcdef.jpg"),
            "file:///android_asset/musaka.jpg", "", "12-0123456789abcdef.jpg"
        )) assertFalse("'$bad' must not be accepted", c.isOurs(bad))
    }

    @Test fun plainHttp_isNeverUsedUnlessAllowedForTests() {
        val strict = RecipeImageCache(dir, baseUrl = "http://rucek.display.mk", allowHttp = false)
        assertNull(strict.fileName("http://rucek.display.mk/img/$name"))
        val real = RecipeImageCache(dir, baseUrl = "https://rucek.display.mk")
        assertEquals(name, real.fileName("https://rucek.display.mk/img/$name"))
        assertNull(real.fileName("http://rucek.display.mk/img/$name"))
    }

    // ---------- fetching and keeping ----------

    @Test fun aPictureIsFetchedOnce_andThenComesFromThePhone() = runBlocking {
        val c = cache()
        assertNull(c.cached(url()))
        val first = c.fetch(url())
        assertNotNull(first)
        assertArrayEquals(picture, first!!.readBytes())
        assertEquals(name, first.name)
        val second = c.fetch(url())
        assertEquals(first, second)
        assertEquals("the second time nothing is asked of the server", 1, fake.requests.size)
        assertEquals("GET", fake.requests.single().method)
        assertEquals("/img/$name", fake.requests.single().path)
        assertEquals(first, c.cached(url()))
    }

    @Test fun twoScreensAskingTogether_causeOneDownload() = runBlocking {
        fake.handler = { Reply(200, bytes = picture, delayMs = 150) }
        val c = cache()
        val files = (1..6).map { async(kotlinx.coroutines.Dispatchers.Default) { c.fetch(url()) } }.awaitAll()
        assertTrue(files.all { it != null && it == files[0] })
        assertEquals(1, fake.requests.size)
    }

    @Test fun whenTheServerCannotBeReached_thereIsNoPicture_andNothingIsLeftBehind() = runBlocking {
        val dead = FakeServer().also { it.stop() }
        val c = RecipeImageCache(dir, baseUrl = dead.baseUrl, allowHttp = true, connectTimeoutMs = 500, readTimeoutMs = 500, validate = { true })
        assertNull(c.fetch("${dead.baseUrl}/img/$name"))
        assertTrue(dir.listFiles().orEmpty().isEmpty())
    }

    @Test fun refusals_andOddAnswers_giveNoPicture_andKeepNothing() = runBlocking {
        for (code in listOf(404, 403, 500, 302, 204)) {
            fake.handler = { Reply(code, headers = if (code == 302) mapOf("Location" to "/elsewhere") else emptyMap()) }
            assertNull("HTTP $code", cache().fetch(url()))
        }
        fake.handler = { Reply(200, bytes = ByteArray(0)) }
        assertNull("an empty answer", cache().fetch(url()))
        assertTrue(dir.listFiles().orEmpty().isEmpty())
    }

    @Test fun aRedirectIsNeverFollowed() = runBlocking {
        fake.handler = { Reply(302, headers = mapOf("Location" to "/img/99-0000000000000000.jpg")) }
        assertNull(cache().fetch(url()))
        assertEquals(1, fake.requests.size)
    }

    @Test fun aPictureOverTheSizeLimit_isRefused() = runBlocking {
        fake.handler = { Reply(200, bytes = ByteArray(RecipeImageCache.MAX_BYTES + 1)) }
        assertNull(cache().fetch(url()))
        assertTrue(dir.listFiles().orEmpty().isEmpty())
        fake.handler = { Reply(200, bytes = ByteArray(RecipeImageCache.MAX_BYTES)) }
        assertNotNull("exactly at the limit is fine", cache().fetch(url("13-0123456789abcdef.jpg")))
    }

    @Test fun somethingThatIsNotAPicture_isNotKept() = runBlocking {
        val c = cache(validate = { false })
        assertNull(c.fetch(url()))
        assertNull(c.cached(url()))
        assertTrue(dir.listFiles().orEmpty().isEmpty())
        // and it is asked again next time (maybe the server was wrong the first time)
        assertNull(c.fetch(url()))
        assertEquals(2, fake.requests.size)
    }

    @Test fun anAddressThatIsNotOurs_neverReachesTheNetwork() = runBlocking {
        val c = cache()
        assertNull(c.fetch("https://evil.example/img/$name"))
        assertNull(c.fetch(url("../secret.jpg")))
        assertNull(c.fetch("file:///android_asset/musaka.jpg"))
        assertTrue(fake.requests.isEmpty())
    }

    // ---------- prefetch and tidying up ----------

    @Test fun prefetch_fetchesOnlyWhatIsMissing_andSkipsFailures() = runBlocking {
        val c = cache()
        c.fetch(url("1-aaaaaaaaaaaaaaaa.jpg"))
        fake.requests.clear()
        fake.handler = { r -> if (r.path.contains("3-cccccccccccccccc")) Reply(404) else Reply(200, bytes = picture) }
        val n = c.prefetch(listOf(url("1-aaaaaaaaaaaaaaaa.jpg"), url("2-bbbbbbbbbbbbbbbb.jpg"), url("3-cccccccccccccccc.jpg"), url("2-bbbbbbbbbbbbbbbb.jpg"), "https://evil.example/x"))
        assertEquals(1, n)
        assertEquals("1 was cached, 2 fetched once, 3 tried and refused, the stranger never asked", 2, fake.requests.size)
        assertNotNull(c.cached(url("2-bbbbbbbbbbbbbbbb.jpg")))
        assertNull(c.cached(url("3-cccccccccccccccc.jpg")))
    }

    @Test fun trim_keepsTheNewestPictures_andNeverTouchesOtherFiles() {
        val c = cache()
        dir.mkdirs()
        val names = (1..5).map { "$it-${"%016x".format(it)}.jpg" }
        names.forEachIndexed { i, n -> File(dir, n).apply { writeBytes(byteArrayOf(1)); setLastModified(1_000_000L + i * 1000) } }
        val other = File(dir, "notes.txt").apply { writeText("mine") }
        c.trim(keep = 3)
        assertEquals(names.takeLast(3).sorted(), dir.listFiles { f -> f.name.endsWith(".jpg") }!!.map { it.name }.sorted())
        assertTrue(other.exists())
    }
}
