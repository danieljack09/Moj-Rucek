package com.mojrucek.app.repository

import org.junit.Assert.assertEquals
import org.junit.Test

class PictureFocusTest {
    private val url = "https://rucek.display.mk/img/1-0123456789abcdef.jpg"

    @Test fun middleOrMissingPoint_leavesTheAddressAlone() {
        assertEquals(url, PictureFocus.attach(url, null, null))
        assertEquals(url, PictureFocus.attach(url, 0.5f, 0.5f))
        assertEquals(url, PictureFocus.attach(url, 0.3f, null))
    }

    @Test fun invalidPoints_areIgnored() {
        for ((x, y) in listOf(-0.1f to 0.5f, 0.5f to 1.1f, Float.NaN to 0.5f, 0.5f to Float.POSITIVE_INFINITY)) {
            assertEquals("$x,$y", url, PictureFocus.attach(url, x, y))
        }
    }

    @Test fun aPoint_roundTrips_andNeverChangesTheFetchedAddress() {
        val with = PictureFocus.attach(url, 0.25f, 0.8f)
        assertEquals(PictureFocus.Point(0.25f, 0.8f), PictureFocus.of(with))
        assertEquals(url, PictureFocus.strip(with))
        assertEquals(PictureFocus.Point(0f, 1f), PictureFocus.of(PictureFocus.attach(url, 0f, 1f)))
    }

    @Test fun noPoint_orGarbage_meansTheMiddle() {
        for (u in listOf(null, "", url, "$url#", "$url#focus=", "$url#focus=1", "$url#focus=a,b", "$url#focus=2,0.5", "$url#focus=0.5,0.5,0.5", "$url#other=0.1,0.1", "$url#focus=NaN,0.5")) {
            assertEquals(u, PictureFocus.MIDDLE, PictureFocus.of(u))
        }
    }

    @Test fun assetAddresses_haveNoPoint() {
        assertEquals(PictureFocus.MIDDLE, PictureFocus.of("file:///android_asset/musaka.jpg"))
    }
}
