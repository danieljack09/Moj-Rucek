package com.mojrucek.app.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class BottomBarScrollStateTest {
    private fun state(max: Float = 100f) = BottomBarScrollState().apply { maxOffset = max }

    @Test fun scrollingDown_slidesTheBarAway_butNeverFurtherThanItsHeight() {
        val s = state()
        s.onScroll(-30f); assertEquals(30f, s.offset, 0f)
        s.onScroll(-500f); assertEquals(100f, s.offset, 0f)
    }

    @Test fun scrollingBackUpALittle_bringsItBack() {
        val s = state()
        s.onScroll(-500f)
        s.onScroll(40f); assertEquals(60f, s.offset, 0f)
        s.onScroll(500f); assertEquals(0f, s.offset, 0f)
    }

    @Test fun neverBelowTheScreenBottomEdge_whenAlreadyVisible() {
        val s = state()
        s.onScroll(80f); assertEquals(0f, s.offset, 0f)
    }

    @Test fun unknownHeight_keepsTheBarInPlace() {
        val s = state(0f)
        s.onScroll(-200f); assertEquals(0f, s.offset, 0f)
    }

    @Test fun whenScrollingStops_theBarRestsFullyShownOrFullyHidden() {
        val s = state()
        s.onScroll(-20f); assertEquals("a tiny nudge down does not hide it", 0f, s.restingOffset(), 0f)
        s.onScroll(-40f); assertEquals(100f, s.restingOffset(), 0f)
        s.onScroll(5f); assertEquals("any scroll back up shows it", 0f, s.restingOffset(), 0f)
        s.onScroll(-50f); assertEquals("and scrolling down hides it again", 100f, s.restingOffset(), 0f)
        s.moveTo(250f); assertEquals("never below the screen edge", 100f, s.offset, 0f)
        s.moveTo(-5f); assertEquals(0f, s.offset, 0f)
    }

    @Test fun reset_showsTheBar() {
        val s = state()
        s.onScroll(-70f); s.reset(); assertEquals(0f, s.offset, 0f)
    }
}
