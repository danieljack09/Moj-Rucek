package com.mojrucek.app.domain.util

import org.junit.Assert.assertEquals
import org.junit.Test

class MacedonianSearchTest {

    @Test
    fun `zacin matches зачин`() {
        assertEquals(
            MacedonianSearch.normalizeForSearch("зачин"),
            MacedonianSearch.normalizeForSearch("zacin")
        )
    }

    @Test
    fun `caj matches чај`() {
        assertEquals(
            MacedonianSearch.normalizeForSearch("чај"),
            MacedonianSearch.normalizeForSearch("caj")
        )
    }

    @Test
    fun `sirenje matches сирење`() {
        assertEquals(
            MacedonianSearch.normalizeForSearch("сирење"),
            MacedonianSearch.normalizeForSearch("sirenje")
        )
    }

    @Test
    fun `non-matching words stay different`() {
        assertEquals(false, MacedonianSearch.normalizeForSearch("зачин") == MacedonianSearch.normalizeForSearch("leb"))
    }
}
