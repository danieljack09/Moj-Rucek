package com.mojrucek.app.ui.auth

import com.mojrucek.app.ui.theme.Spacing
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mojrucek.app.BuildConfig
import com.mojrucek.app.di.AppContainer
import com.mojrucek.app.ui.components.GenericViewModelFactory
import com.mojrucek.app.ui.navigation.MojRucekNavHost

enum class AuthGateTarget { APP, WELCOME, CONFIG_ERROR }

/**
 * Who may see what:
 *  - signed in (guest or Google, including a session Firebase persisted on disk, offline too) -> app;
 *  - accounts configured but nobody signed in -> Welcome (Google or Guest, nothing else);
 *  - accounts NOT configured (no google-services.json): only builds that opt in via
 *    [allowUnconfigured] (debug) run without accounts; every other build is blocked.
 */
internal fun authGateTarget(available: Boolean, signedIn: Boolean, allowUnconfigured: Boolean): AuthGateTarget = when {
    signedIn -> AuthGateTarget.APP
    !available -> if (allowUnconfigured) AuthGateTarget.APP else AuthGateTarget.CONFIG_ERROR
    else -> AuthGateTarget.WELCOME
}

@Composable
fun AuthGate(container: AppContainer) {
    val account by container.authRepository.state.collectAsState()

    when (authGateTarget(account.available, account.signedIn, BuildConfig.ALLOW_UNCONFIGURED_AUTH)) {
        AuthGateTarget.APP -> {
            MojRucekNavHost(container)
            // Shown only when an account signed in and the data from before accounts still needs a decision.
            LegacyDataPrompt(container)
        }
        AuthGateTarget.WELCOME -> {
            val vm: AccountViewModel = viewModel(factory = GenericViewModelFactory {
                AccountViewModel(container.authRepository, container.accountSwitchRepository)
            })
            WelcomeScreen(vm)
        }
        AuthGateTarget.CONFIG_ERROR -> ConfigErrorScreen()
    }
}

/** Fully blocking on purpose: no button, no retry. Only a correctly built app can fix this. */
@Composable
private fun ConfigErrorScreen() {
    // Fully blocking on purpose: no button, no retry. Laid out like EmptyState, with the app mark on top.
    Scaffold { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 32.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            AppMark(size = 80.dp)
            Spacer(Modifier.height(Spacing.betweenSections))
            Text(
                "Апликацијата не е правилно подесена",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Оваа верзија нема конфигурација за сметки и не може да се користи. Инсталирај ја официјалната верзија на апликацијата.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}
