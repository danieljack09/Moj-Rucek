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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.mojrucek.app.R

/**
 * First screen when nobody is signed in. Guest or Google; "later" enters the app for this session
 * only (e.g. offline first launch), without creating an account.
 */
@Composable
fun WelcomeScreen(viewModel: AccountViewModel, onSkip: () -> Unit) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current

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

            Spacer(Modifier.height(24.dp))
            if (state.busy) {
                CircularProgressIndicator(Modifier.size(32.dp))
            } else {
                TextButton(onClick = onSkip) { Text("Подоцна") }
            }
        }
    }

    AccountMessageDialog(state.message, viewModel::dismissMessage)
}
