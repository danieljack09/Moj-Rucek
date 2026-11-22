package com.mojrucek.app.ui.auth

import org.junit.Assert.assertEquals
import org.junit.Test

class AuthGateTargetTest {

    private fun target(available: Boolean, signedIn: Boolean, allowUnconfigured: Boolean) =
        authGateTarget(available, signedIn, allowUnconfigured)

    @Test fun signedIn_opensTheApp_inEveryBuild() {
        assertEquals(AuthGateTarget.APP, target(available = true, signedIn = true, allowUnconfigured = false))
        assertEquals(AuthGateTarget.APP, target(available = true, signedIn = true, allowUnconfigured = true))
    }

    @Test fun signedOut_withAccounts_showsWelcome_inEveryBuild() {
        // Signed out + (offline or online) never reaches the app: only Welcome, whose sign-in
        // buttons report the network error.
        assertEquals(AuthGateTarget.WELCOME, target(available = true, signedIn = false, allowUnconfigured = false))
        assertEquals(AuthGateTarget.WELCOME, target(available = true, signedIn = false, allowUnconfigured = true))
    }

    @Test fun unconfigured_debug_fallbackOpensTheApp() {
        assertEquals(AuthGateTarget.APP, target(available = false, signedIn = false, allowUnconfigured = true))
    }

    @Test fun unconfigured_release_isBlocked_neverTheAppNorWelcome() {
        assertEquals(AuthGateTarget.CONFIG_ERROR, target(available = false, signedIn = false, allowUnconfigured = false))
    }
}
