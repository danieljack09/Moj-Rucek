package com.mojrucek.app.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.mojrucek.app.R

/**
 * First screen when nobody is signed in. Guest or Google; there is no way into the app without
 * one of them (offline included: the sign-in error is shown and the user stays here).
 */
@Composable
fun WelcomeScreen(viewModel: AccountViewModel) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var showEmail by remember { mutableStateOf(false) }

    Scaffold { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("🍲", style = MaterialTheme.typography.displayLarge)
            Spacer(Modifier.height(16.dp))
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(8.dp))
            Text(
                "Планирај ручеци, следи ги намирниците и подготви листа за пазарење.",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(40.dp))

            Button(
                onClick = { viewModel.continueWithGoogle(context) },
                enabled = !state.busy,
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) { Text("Продолжи со Google") }
            Spacer(Modifier.height(12.dp))
            OutlinedButton(
                onClick = { showEmail = true },
                enabled = !state.busy,
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) { Text("Продолжи со е-пошта") }
            Spacer(Modifier.height(12.dp))
            OutlinedButton(
                onClick = viewModel::continueAsGuest,
                enabled = !state.busy,
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) { Text("Продолжи како гостин") }
            Spacer(Modifier.height(12.dp))
            Text(
                "Како гостин можеш веднаш да почнеш. Подоцна можеш да поврзеш Google сметка без да ги изгубиш податоците.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            if (state.busy) {
                Spacer(Modifier.height(24.dp))
                CircularProgressIndicator(Modifier.size(32.dp))
            }
        }
    }

    if (showEmail) EmailAuthDialog(viewModel, isGuest = false, onDismiss = { showEmail = false })
    AccountMessageDialog(state.message, viewModel::dismissMessage)
}
