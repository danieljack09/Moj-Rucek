package com.mojrucek.app.ui.auth

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mojrucek.app.di.AppContainer
import com.mojrucek.app.ui.components.GenericViewModelFactory
import com.mojrucek.app.ui.navigation.MojRucekNavHost

/**
 * Shows [WelcomeScreen] while nobody is signed in, otherwise the app. Builds without
 * google-services.json (accounts unavailable) go straight to the app, as before.
 */
@Composable
fun AuthGate(container: AppContainer) {
    val account by container.authRepository.state.collectAsState()
    var skipped by rememberSaveable { mutableStateOf(false) }
    // "Подоцна" only skips until the next sign-in: a later sign-out shows Welcome again.
    LaunchedEffect(account.signedIn) { if (account.signedIn) skipped = false }

    if (!account.available || account.signedIn || skipped) {
        MojRucekNavHost(container)
    } else {
        val vm: AccountViewModel = viewModel(factory = GenericViewModelFactory {
            AccountViewModel(container.authRepository, container.accountSwitchRepository)
        })
        WelcomeScreen(vm, onSkip = { skipped = true })
    }
}
