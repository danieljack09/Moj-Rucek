package com.mojrucek.app.ui.components

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.runtime.getValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.unit.Velocity
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue

/**
 * How far the bottom navigation bar has slid down off the screen. Scrolling the list down (finger up) slides the bar away with
 * the finger; scrolling back up even a little brings it back, so lists get more room without losing the navigation.
 */
class BottomBarScrollState {
    /** Height of the bar in pixels (measured by the layout); 0 until known, which keeps the bar in place. */
    var maxOffset by mutableFloatStateOf(0f)

    /** 0 = fully visible, [maxOffset] = fully hidden. */
    var offset by mutableFloatStateOf(0f)
        private set

    /** [deltaY] is the scroll amount as the finger moves: negative = list moving up (scrolling down), positive = scrolling back up. */
    fun onScroll(deltaY: Float) {
        if (maxOffset <= 0f) return
        if (deltaY != 0f) lastWasUp = deltaY > 0f
        offset = (offset - deltaY).coerceIn(0f, maxOffset)
    }

    private var lastWasUp = false

    /**
     * Where the bar comes to rest when scrolling stops: after any scroll back up it is fully shown; after scrolling down it is
     * fully hidden once it has slid more than a quarter of the way (a tiny nudge down does not hide it).
     */
    fun restingOffset(): Float = if (lastWasUp || offset <= maxOffset / 4f) 0f else maxOffset

    /** Moves the bar to [value] (used by the settle animation). */
    fun moveTo(value: Float) { offset = value.coerceIn(0f, maxOf(maxOffset, 0f)) }

    /** Brings the bar back (for example when another screen opens). */
    fun reset() { offset = 0f }
}

/**
 * Watches the scrolling of the lists below it (without taking any of it) and drives a [BottomBarScrollState]:
 * down slides the thing away, a small scroll up brings it back, and when the finger lifts it comes to rest fully shown or hidden.
 */
class HideOnScrollConnection(private val state: BottomBarScrollState) : NestedScrollConnection {
    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
        state.onScroll(available.y)
        return Offset.Zero
    }

    override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
        if (state.maxOffset > 0f && state.offset != state.restingOffset()) {
            animate(state.offset, state.restingOffset(), animationSpec = tween(150)) { v, _ -> state.moveTo(v) }
        }
        return Velocity.Zero
    }
}
