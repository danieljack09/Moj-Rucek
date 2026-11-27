package com.mojrucek.app.ui.auth

import com.mojrucek.app.ui.components.AppProgressIndicator
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.mojrucek.app.R
import com.mojrucek.app.ui.components.PillTextButton
import com.mojrucek.app.ui.components.PrimaryPillButton
import com.mojrucek.app.ui.theme.MojRucekTheme
import com.mojrucek.app.ui.theme.PillShape
import com.mojrucek.app.ui.theme.Spacing

/**
 * First screen when nobody is signed in. Guest or Google; there is no way into the app without
 * one of them (offline included: the sign-in error is shown and the user stays here).
 *
 * Layout from the reference sign-in screen: the app mark, the title and subtitle, the email option
 * as the main pill, an "или" divider, Google as a greenContainer pill with the G logo, and the guest
 * option as a text link with its explanation.
 */
@Composable
fun WelcomeScreen(viewModel: AccountViewModel) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var showEmail by remember { mutableStateOf(false) }

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.screenHorizontal, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            AppMark()
            Spacer(Modifier.height(Spacing.betweenSections))
            Text(
                stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
                modifier = Modifier.semantics { heading() }
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Планирај ручеци, следи ги намирниците и подготви листа за пазарење.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(40.dp))

            PrimaryPillButton(
                "Продолжи со е-пошта",
                onClick = { showEmail = true },
                enabled = !state.busy,
                modifier = Modifier.fillMaxWidth()
            )
            OrDivider(Modifier.padding(vertical = Spacing.betweenCards))
            GooglePillButton(
                "Продолжи со Google",
                onClick = { viewModel.continueWithGoogle(context) },
                enabled = !state.busy
            )
            Spacer(Modifier.height(Spacing.betweenCards))
            PillTextButton(
                "Продолжи како гостин",
                onClick = viewModel::continueAsGuest,
                enabled = !state.busy
            )
            Text(
                "Како гостин можеш веднаш да почнеш. Подоцна можеш да поврзеш Google сметка без да ги изгубиш податоците.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            if (state.busy) {
                Spacer(Modifier.height(24.dp))
                AppProgressIndicator(size = 32.dp)
            }
        }
    }

    if (showEmail) EmailAuthDialog(viewModel, isGuest = false, onDismiss = { showEmail = false })
    AccountMessageDialog(state.message, viewModel::dismissMessage)
}

/** The app mark from 02-app-icon: a white line chef hat with a flame on a green rounded square. Decorative. */
@Composable
internal fun AppMark(size: androidx.compose.ui.unit.Dp = 96.dp) {
    Box(
        Modifier.size(size).clip(RoundedCornerShape(size * 0.28f)).background(MojRucekTheme.colors.green),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painterResource(R.drawable.ic_app_mark),
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(size * 0.66f)
        )
    }
}

/** Thin lines on both sides of "или", between two sign-in options. */
@Composable
private fun OrDivider(modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        HorizontalDivider(Modifier.weight(1f), thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)
        Text(
            "или",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 12.dp)
        )
        HorizontalDivider(Modifier.weight(1f), thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)
    }
}

/** greenContainer pill, 52dp, with the multicolour G logo and the label in the text colour. */
@Composable
private fun GooglePillButton(text: String, onClick: () -> Unit, enabled: Boolean) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
        shape = PillShape,
        color = MojRucekTheme.colors.greenContainer,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        Row(
            Modifier.padding(horizontal = 24.dp).alpha(if (enabled) 1f else 0.38f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Image(painterResource(R.drawable.ic_google_g), contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            Text(text, style = MaterialTheme.typography.labelLarge)
        }
    }
}
