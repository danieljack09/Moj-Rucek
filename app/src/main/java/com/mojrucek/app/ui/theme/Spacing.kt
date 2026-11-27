package com.mojrucek.app.ui.theme

import androidx.compose.ui.unit.dp

/** Standard spacing from the spec's "Shape and spacing" section. */
object Spacing {
    /** Horizontal padding at the edges of every screen. */
    val screenHorizontal = 20.dp

    /** Vertical gap between cards. */
    val betweenCards = 16.dp

    /** Vertical gap between sections. */
    val betweenSections = 24.dp

    /** Padding inside a card. */
    val cardPadding = 20.dp

    /** Padding inside a list row. */
    val rowPadding = 12.dp

    /** Height of the floating pill navigation bar (five 48dp circles plus 8dp above and below). */
    val navBarHeight = 64.dp

    /** Gap between the floating nav bar and the screen sides and bottom (plus the system inset). */
    val navBarMargin = 16.dp

    /**
     * Bottom content padding for tab-screen lists so the last item clears the
     * floating bar: bar height + its bottom margin + 16dp breathing room.
     */
    val navBarClearance = navBarHeight + navBarMargin + 16.dp

    /** Lift for a FAB so it sits 16dp above the floating bar. */
    val fabAboveNavBar = navBarHeight + navBarMargin

    /** List bottom padding on tab screens that also have a FAB (56dp FAB + 16dp gap). */
    val navBarClearanceWithFab = navBarClearance + 56.dp + 16.dp
}
