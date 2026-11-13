package com.mojrucek.app.ui.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

/**
 * Reserved spot for a future adaptive banner ad (Free users only).
 *
 * No ad SDK is integrated yet. When [content] is null the slot emits nothing, so it occupies
 * zero height and leaves no gap. Callers should also skip surrounding spacing in that case
 * (see HomeScreen, which only adds the list item when an ad composable is supplied).
 */
@Composable
fun AdBannerSlot(
    modifier: Modifier = Modifier,
    content: (@Composable () -> Unit)? = null
) {
    if (content == null) return
    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}
