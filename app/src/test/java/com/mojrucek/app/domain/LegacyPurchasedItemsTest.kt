package com.mojrucek.app.domain

import com.mojrucek.app.data.entity.ShoppingListItem
import com.mojrucek.app.domain.unit.MeasurementUnit
import com.mojrucek.app.domain.usecase.LegacyPurchasedItems
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LegacyPurchasedItemsTest {

    private fun item(id: Long, purchased: Boolean, handled: Boolean) = ShoppingListItem(
        id = id, name = "n$id", normalizedName = "n$id", quantity = 1.0, unit = MeasurementUnit.PIECE,
        isPurchased = purchased, isHandled = handled
    )

    @Test fun purchasedAndHandled_isDeleted() {
        val plan = LegacyPurchasedItems.plan(listOf(item(1, purchased = true, handled = true)))
        assertEquals(listOf(1L), plan.deleteIds)
        assertTrue(plan.reopen.isEmpty())
    }

    @Test fun purchasedButNotHandled_isReopenedNotDeleted_soTheDecisionIsNotLost() {
        val plan = LegacyPurchasedItems.plan(listOf(item(2, purchased = true, handled = false)))
        assertTrue(plan.deleteIds.isEmpty())
        val reopened = plan.reopen.single()
        assertEquals(2L, reopened.id)
        assertFalse(reopened.isPurchased)
        assertFalse(reopened.isHandled)
        assertEquals("n2", reopened.name)
        assertEquals(1.0, reopened.quantity, 0.0)
    }

    @Test fun activeItems_areNeverTouched() {
        val plan = LegacyPurchasedItems.plan(listOf(item(3, purchased = false, handled = false)))
        assertTrue(plan.isEmpty)
    }

    @Test fun mixedList_isSplitCorrectly() {
        val plan = LegacyPurchasedItems.plan(
            listOf(
                item(1, true, true), item(2, true, false), item(3, false, false), item(4, true, true)
            )
        )
        assertEquals(listOf(1L, 4L), plan.deleteIds)
        assertEquals(listOf(2L), plan.reopen.map { it.id })
    }

    @Test fun runningItAgainOnTheResult_changesNothing() {
        val first = LegacyPurchasedItems.plan(listOf(item(2, true, false)))
        assertTrue(LegacyPurchasedItems.plan(first.reopen).isEmpty)
    }

    @Test fun emptyList_isEmpty() {
        assertTrue(LegacyPurchasedItems.plan(emptyList()).isEmpty)
    }
}
