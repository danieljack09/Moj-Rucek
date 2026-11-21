package com.mojrucek.app.auth

import android.content.Context
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.AuthCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await

/** How the current Firebase user signs in. EMAIL is reserved for email/password (not offered yet). */
enum class AccountKind { GUEST, GOOGLE, EMAIL, OTHER }

data class AccountState(
    /** False when this build has no google-services.json: the app then works without accounts. */
    val available: Boolean,
    val uid: String? = null,
    val kind: AccountKind? = null,
    val email: String? = null
) {
    val signedIn: Boolean get() = uid != null
}

sealed interface AuthResult {
    data object Success : AuthResult
    data object Cancelled : AuthResult
    /**
     * Linking failed because the credential already belongs to another Firebase account.
     * Nothing was changed. The credential is kept in [AuthRepository] for the explicit
     * switch flow ([AuthRepository.signInWithPendingCredential]).
     */
    data class CredentialInUse(val email: String?) : AuthResult
    data class Error(val message: String) : AuthResult
}

/**
 * Firebase Authentication is used for identity only (guest = anonymous, Google).
 * A guest upgrades by LINKING a credential to the same Firebase user, so the uid and all
 * local data stay. This class never deletes data and never merges accounts.
 *
 * New sign-in methods (e.g. email/password) only need to build an [AuthCredential] and call
 * [signInOrLink].
 */
class AuthRepository(private val appContext: Context) {

    private val available = FirebaseApp.getApps(appContext).isNotEmpty()
    private val auth: FirebaseAuth? = if (available) FirebaseAuth.getInstance() else null

    private val _state = MutableStateFlow(readState())
    val state: StateFlow<AccountState> = _state.asStateFlow()

    /** Credential that could not be linked because another account owns it. In memory only. */
    private var pendingConflict: AuthCredential? = null

    init {
        auth?.addAuthStateListener { refresh() }
        auth?.addIdTokenListener(FirebaseAuth.IdTokenListener { refresh() })
    }

    private fun readState(): AccountState {
        val user = auth?.currentUser ?: return AccountState(available = available)
        return AccountState(available = true, uid = user.uid, kind = kindOf(user), email = user.email)
    }

    private fun refresh() { _state.value = readState() }

    private fun kindOf(user: FirebaseUser): AccountKind {
        val providers = user.providerData.map { it.providerId }
        return when {
            GoogleAuthProvider.PROVIDER_ID in providers -> AccountKind.GOOGLE
            "password" in providers -> AccountKind.EMAIL
            user.isAnonymous -> AccountKind.GUEST
            else -> AccountKind.OTHER
        }
    }

    suspend fun continueAsGuest(): AuthResult {
        val auth = auth ?: return notAvailable()
        if (auth.currentUser != null) return AuthResult.Success
        return try {
            auth.signInAnonymously().await()
            refresh()
            AuthResult.Success
        } catch (e: Exception) {
            AuthResult.Error(AuthErrors.message(e))
        }
    }

    /**
     * Shows the Google account picker. [activityContext] must be an Activity context.
     * Guest -> links Google to the same user. No user -> signs in. Already Google -> nothing to do.
     */
    suspend fun continueWithGoogle(activityContext: Context): AuthResult {
        if (auth == null) return notAvailable()
        val credential = when (val r = requestGoogleCredential(activityContext)) {
            is GoogleCredentialResult.Ok -> r.credential
            is GoogleCredentialResult.Failed -> return r.result
        }
        return signInOrLink(credential)
    }

    /** Links [credential] to the current guest, or signs in with it when nobody is signed in. */
    suspend fun signInOrLink(credential: AuthCredential): AuthResult {
        val auth = auth ?: return notAvailable()
        val user = auth.currentUser
        return try {
            when {
                user == null -> auth.signInWithCredential(credential).await()
                user.isAnonymous -> user.linkWithCredential(credential).await()
                else -> return AuthResult.Error("Веќе си најавен со сметка.")
            }
            // linkWithCredential already updates currentUser; no reload (a failed reload would
            // wrongly report an error after a successful link).
            refresh()
            AuthResult.Success
        } catch (e: FirebaseAuthUserCollisionException) {
            if (user == null) {
                // Plain sign-in collided (e.g. account-exists-with-different-credential): there is
                // no guest to protect, so no switch flow; just report it.
                AuthResult.Error(AuthErrors.message(e))
            } else {
                // credential-already-in-use while linking a guest: nothing changed.
                pendingConflict = e.updatedCredential ?: credential
                AuthResult.CredentialInUse(e.email)
            }
        } catch (e: Exception) {
            AuthResult.Error(AuthErrors.message(e))
        }
    }

    /**
     * Signs in to the account that owns the pending credential. The current guest account is
     * left behind (anonymous accounts cannot be signed into again). Call only from the explicit,
     * confirmed switch flow, after the local snapshot was verified.
     */
    suspend fun signInWithPendingCredential(): AuthResult {
        val auth = auth ?: return notAvailable()
        val credential = pendingConflict ?: return AuthResult.Error("Нема сметка за префрлање. Обиди се повторно.")
        return try {
            auth.signInWithCredential(credential).await()
            pendingConflict = null
            refresh()
            AuthResult.Success
        } catch (e: Exception) {
            AuthResult.Error(AuthErrors.message(e))
        }
    }

    fun clearPendingConflict() { pendingConflict = null }

    /**
     * Signs out. Local data on the phone is not touched. Guests are never offered this.
     *
     * TEMPORARY (Stage A, not production-ready): local data has no owner yet, so it stays visible
     * to whoever signs in next. Data ownership (Room v4) comes with the server stage; see README,
     * "Accounts: temporary limitations".
     */
    suspend fun signOut() {
        auth?.signOut()
        pendingConflict = null
        try {
            CredentialManager.create(appContext).clearCredentialState(ClearCredentialStateRequest())
        } catch (_: Exception) {
            // Only affects which Google account is pre-selected next time.
        }
        refresh()
    }

    /** Firebase ID token for our own server (Stage B). Null when signed out or unavailable. */
    suspend fun idToken(forceRefresh: Boolean = false): String? =
        auth?.currentUser?.getIdToken(forceRefresh)?.await()?.token

    // ------------------------------------------------------------------------------------------

    private sealed interface GoogleCredentialResult {
        data class Ok(val credential: AuthCredential) : GoogleCredentialResult
        data class Failed(val result: AuthResult) : GoogleCredentialResult
    }

    private suspend fun requestGoogleCredential(activityContext: Context): GoogleCredentialResult {
        val clientId = webClientId()
            ?: return GoogleCredentialResult.Failed(AuthResult.Error("Google најавата не е подесена во оваа верзија."))
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(GetSignInWithGoogleOption.Builder(clientId).build())
            .build()
        return try {
            val response = CredentialManager.create(activityContext).getCredential(activityContext, request)
            val credential = response.credential
            if (credential is CustomCredential &&
                credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            ) {
                val google = GoogleIdTokenCredential.createFrom(credential.data)
                GoogleCredentialResult.Ok(GoogleAuthProvider.getCredential(google.idToken, null))
            } else {
                GoogleCredentialResult.Failed(AuthResult.Error("Неочекуван одговор од Google. Обиди се повторно."))
            }
        } catch (e: GetCredentialCancellationException) {
            GoogleCredentialResult.Failed(AuthResult.Cancelled)
        } catch (e: NoCredentialException) {
            GoogleCredentialResult.Failed(
                AuthResult.Error("На телефонот нема Google сметка. Додај сметка во поставките на телефонот и обиди се повторно.")
            )
        } catch (e: GetCredentialException) {
            GoogleCredentialResult.Failed(AuthResult.Error("Google најавата не успеа (${e.type}). Обиди се повторно."))
        } catch (e: GoogleIdTokenParsingException) {
            GoogleCredentialResult.Failed(AuthResult.Error("Неважечки одговор од Google. Обиди се повторно."))
        }
    }

    /**
     * Generated by the google-services plugin from google-services.json. Looked up by name so the
     * app still compiles when that (git-ignored) file is absent.
     */
    private fun webClientId(): String? {
        val id = appContext.resources.getIdentifier("default_web_client_id", "string", appContext.packageName)
        return if (id == 0) null else appContext.getString(id).takeIf { it.isNotBlank() }
    }

    private fun notAvailable() = AuthResult.Error("Сметките не се достапни во оваа верзија на апликацијата.")
}

internal object AuthErrors {
    fun message(e: Exception): String = when (e) {
        is FirebaseNetworkException -> "Нема интернет врска. Провери ја врската и обиди се повторно."
        is FirebaseAuthException -> when (e.errorCode) {
            "ERROR_USER_DISABLED" -> "Оваа сметка е оневозможена."
            "ERROR_TOO_MANY_REQUESTS" -> "Премногу обиди. Почекај малку и обиди се повторно."
            "ERROR_INVALID_CREDENTIAL" -> "Најавата истече или е неважечка. Обиди се повторно."
            "ERROR_OPERATION_NOT_ALLOWED" -> "Овој начин на најава не е овозможен."
            "ERROR_ACCOUNT_EXISTS_WITH_DIFFERENT_CREDENTIAL" ->
                "Веќе постои сметка со оваа е-пошта, но со друг начин на најава."
            else -> "Најавата не успеа (${e.errorCode}). Обиди се повторно."
        }
        else -> "Најавата не успеа. Обиди се повторно."
    }
}
