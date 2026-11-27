package com.mojrucek.app.ui.components

import android.content.res.Configuration
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.mojrucek.app.ui.theme.MojRucekTheme
import com.mojrucek.app.ui.theme.Spacing

/** Shows a preview twice: once in light mode and once in dark mode. */
@Preview(name = "Светла", showBackground = true)
@Preview(name = "Темна", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
annotation class LightDarkPreviews

/** Wraps preview content in the app theme on the page background. */
@Composable
internal fun PreviewSurface(content: @Composable () -> Unit) {
    MojRucekTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            Box(Modifier.padding(Spacing.screenHorizontal)) { content() }
        }
    }
}
