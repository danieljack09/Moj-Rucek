package com.mojrucek.app.domain.session

import org.junit.Assert.assertEquals
import org.junit.Test

class GuestTextsTest {
    @Test fun days_usesTheSingularOnlyForOne() {
        assertEquals("1 ден", GuestTexts.days(1))
        for (n in listOf(0, 2, 3, 11, 14, 21)) assertEquals("$n дена", GuestTexts.days(n))
    }

    @Test fun settingsLine_followsTheStatus() {
        assertEquals("Гостин · уште 14 дена", GuestTexts.settingsLine(GuestPeriod.Status.Running(14)))
        assertEquals("Гостин · уште 1 ден", GuestTexts.settingsLine(GuestPeriod.Status.Running(1)))
        assertEquals("Гостин · гостинскиот период заврши", GuestTexts.settingsLine(GuestPeriod.Status.Expired))
        assertEquals("Гостин", GuestTexts.settingsLine(GuestPeriod.Status.NotAGuest))
    }

    @Test fun reminderTitle_saysTomorrowForOneDay() {
        assertEquals("Гостинскиот период завршува утре", GuestTexts.reminderTitle(1))
        assertEquals("Гостинскиот период завршува за 3 дена", GuestTexts.reminderTitle(3))
    }
}
