package com.mojrucek.app.ui.auth

import com.mojrucek.app.domain.auth.EmailInput
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.mojrucek.app.ui.components.CircleIconButton
import com.mojrucek.app.ui.components.PasswordToggle
import com.mojrucek.app.ui.components.PillTextButton
import com.mojrucek.app.ui.components.PillTextField
import com.mojrucek.app.ui.components.PrimaryPillButton
import com.mojrucek.app.ui.theme.Spacing

/**
 * The email/password form: create an account or sign in, and ask for a password reset. [isGuest] adds the reassurance
 * that a guest's data stays. Everything typed is checked first (see EmailInput); Firebase decides the rest.
 *
 * Shown full screen like the reference "Sign in" phone: a back circle ("Откажи"), the title, the guest line as the
 * subtitle, the two pill fields, "Заборавена лозинка?" right-aligned (sign-in only), the main button, and the mode
 * switch as the bottom line. Back, the circle and outside dismissal are all blocked while busy, as before.
 */
@Composable
fun EmailAuthDialog(viewModel: AccountViewModel, isGuest: Boolean, onDismiss: () -> Unit) {
    val state by viewModel.uiState.collectAsState()
    var mode by remember { mutableStateOf(EmailMode.CREATE) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var problem by remember { mutableStateOf<String?>(null) }
    var info by remember { mutableStateOf<String?>(null) }
    // A field shows its helper error only once the user has typed in it and moved on, as in the reference's error state.
    var emailTouched by remember { mutableStateOf(false) }
    var passwordTouched by remember { mutableStateOf(false) }

    fun result(error: String?, success: () -> Unit) { if (error == null) success() else problem = error }

    // The same checks submitEmail runs first (EmailInput); the button is disabled while they fail.
    val normalized = EmailInput.normalize(email)
    val emailProblem = EmailInput.emailProblem(normalized)
    val passwordProblem = if (mode == EmailMode.CREATE) EmailInput.newPasswordProblem(password) else EmailInput.passwordTypedProblem(password)
    val inputValid = emailProblem == null && passwordProblem == null
    val showEmailError = emailTouched && email.isNotEmpty() && emailProblem != null
    val showPasswordError = passwordTouched && password.isNotEmpty() && passwordProblem != null

    Dialog(
        onDismissRequest = { if (!state.busy) onDismiss() },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = !state.busy,
            dismissOnClickOutside = false,
            decorFitsSystemWindows = false
        )
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
            contentColor = MaterialTheme.colorScheme.onBackground
        ) {
            Column(
                Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .imePadding()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = Spacing.screenHorizontal, vertical = 8.dp)
            ) {
                CircleIconButton(
                    Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = "Откажи",
                    onClick = onDismiss,
                    enabled = !state.busy
                )
                Spacer(Modifier.height(Spacing.betweenSections))
                Text(
                    if (mode == EmailMode.CREATE) "Креирај сметка со е-пошта" else "Најави се со е-пошта",
                    style = MaterialTheme.typography.headlineLarge,
                    modifier = Modifier.semantics { heading() }
                )
                if (isGuest) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        if (mode == EmailMode.CREATE) "Твоите податоци остануваат исти."
                        else "Ако се најавиш во постоечка сметка, прво ќе те прашаме што да правиме со податоците на овој телефон.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.height(Spacing.betweenSections))

                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    PillTextField(
                        value = email,
                        onValueChange = { email = it; problem = null; info = null },
                        label = "Е-пошта",
                        enabled = !state.busy,
                        isError = showEmailError,
                        helperText = if (showEmailError) emailProblem else null,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        modifier = Modifier.onFocusChanged { if (!it.hasFocus && email.isNotEmpty()) emailTouched = true }
                    )
                    PillTextField(
                        value = password,
                        onValueChange = { password = it; problem = null; info = null },
                        label = if (mode == EmailMode.CREATE) "Лозинка (најмалку 8 знаци)" else "Лозинка",
                        enabled = !state.busy,
                        isError = showPasswordError,
                        helperText = if (showPasswordError) passwordProblem else null,
                        password = PasswordToggle(showDescription = "Прикажи лозинка", hideDescription = "Сокриј лозинка"),
                        modifier = Modifier.onFocusChanged { if (!it.hasFocus && password.isNotEmpty()) passwordTouched = true }
                    )
                }

                if (mode == EmailMode.SIGN_IN) {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                        PillTextButton(
                            "Заборавена лозинка?",
                            onClick = { viewModel.requestPasswordReset(email) { err -> result(err) { info = "Ако за оваа е-пошта постои сметка, испративме порака за нова лозинка." } } },
                            enabled = !state.busy
                        )
                    }
                }

                // Messages from the server or the reset request.
                problem?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 20.dp))
                }
                info?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 20.dp))
                }

                Spacer(Modifier.height(Spacing.betweenSections))
                PrimaryPillButton(
                    if (mode == EmailMode.CREATE) "Креирај" else "Најави се",
                    onClick = { viewModel.submitEmail(mode, email, password) { err -> result(err) { onDismiss() } } },
                    enabled = inputValid && !state.busy,
                    loading = state.busy,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(Spacing.betweenCards))
                // Bottom line: switch between creating an account and signing in.
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    PillTextButton(
                        if (mode == EmailMode.CREATE) "Веќе имам сметка" else "Нова сметка",
                        onClick = { mode = if (mode == EmailMode.CREATE) EmailMode.SIGN_IN else EmailMode.CREATE; problem = null; info = null },
                        enabled = !state.busy
                    )
                }
            }
        }
    }
}
