package com.mojrucek.app.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

/**
 * The email/password form: create an account or sign in, and ask for a password reset. [isGuest] adds the reassurance
 * that a guest's data stays. Everything typed is checked first (see EmailInput); Firebase decides the rest.
 */
@Composable
fun EmailAuthDialog(viewModel: AccountViewModel, isGuest: Boolean, onDismiss: () -> Unit) {
    val state by viewModel.uiState.collectAsState()
    var mode by remember { mutableStateOf(EmailMode.CREATE) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var problem by remember { mutableStateOf<String?>(null) }
    var info by remember { mutableStateOf<String?>(null) }

    fun result(error: String?, success: () -> Unit) { if (error == null) success() else problem = error }

    AlertDialog(
        onDismissRequest = { if (!state.busy) onDismiss() },
        title = { Text(if (mode == EmailMode.CREATE) "Креирај сметка со е-пошта" else "Најави се со е-пошта") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (isGuest) {
                    Text(
                        if (mode == EmailMode.CREATE) "Твоите податоци остануваат исти."
                        else "Ако се најавиш во постоечка сметка, прво ќе те прашаме што да правиме со податоците на овој телефон.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                OutlinedTextField(
                    value = email, onValueChange = { email = it; problem = null; info = null },
                    label = { Text("Е-пошта") }, singleLine = true, enabled = !state.busy,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Email),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = password, onValueChange = { password = it; problem = null; info = null },
                    label = { Text(if (mode == EmailMode.CREATE) "Лозинка (најмалку 8 знаци)" else "Лозинка") },
                    singleLine = true, enabled = !state.busy,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth()
                )
                problem?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                info?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(
                        onClick = { mode = if (mode == EmailMode.CREATE) EmailMode.SIGN_IN else EmailMode.CREATE; problem = null; info = null },
                        enabled = !state.busy
                    ) { Text(if (mode == EmailMode.CREATE) "Веќе имам сметка" else "Нова сметка") }
                    if (mode == EmailMode.SIGN_IN) {
                        TextButton(
                            onClick = { viewModel.requestPasswordReset(email) { err -> result(err) { info = "Ако за оваа е-пошта постои сметка, испративме порака за нова лозинка." } } },
                            enabled = !state.busy
                        ) { Text("Заборавена лозинка?") }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { viewModel.submitEmail(mode, email, password) { err -> result(err) { onDismiss() } } },
                enabled = !state.busy
            ) { Text(if (mode == EmailMode.CREATE) "Креирај" else "Најави се") }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !state.busy) { Text("Откажи") } }
    )
}
