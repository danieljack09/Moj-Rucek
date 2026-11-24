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
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.mojrucek.app.session.IdTokenSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await

/** How the current Firebase user signs in. */
enum class AccountKind { GUEST, GOOGLE, EMAIL, OTHER }

data class AccountState(
    /** False when this build has no google-services.json: the app then works without accounts. */
    val available: Boolean,
    val uid: String? = null,
    val kind: AccountKind? = null,
    val email: String? = null,
    /** Only meaningful for an email/password account: has the address been confirmed through the link we sent? */
    val emailVerified: Boolean = false
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
class AuthRepository(private val appContext: Context) : IdTokenSource {

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
        return AccountState(available = true, uid = user.uid, kind = kindOf(user), email = user.email, emailVerified = user.isEmailVerified)
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

    // ---------------------------------------------------------------------------------------------
    // Email and password

    /**
     * Creates an email/password account. A guest is upgraded IN PLACE (the credential is linked, same uid, all data
     * stays); with nobody signed in a new account is created. A verification email is sent right away. The account
     * counts as unverified (and the server as a guest) until the address is confirmed.
     */
    suspend fun createEmailAccount(email: String, password: String): AuthResult {
        val auth = auth ?: return notAvailable()
        val user = auth.currentUser
        return try {
            val created = when {
                user == null -> auth.createUserWithEmailAndPassword(email, password).await().user
                user.isAnonymous -> user.linkWithCredential(EmailAuthProvider.getCredential(email, password)).await().user
                else -> return AuthResult.Error("Веќе си најавен со сметка.")
            }
            refresh()
            try { (created ?: auth.currentUser)?.sendEmailVerification()?.await() } catch (_: Exception) { /* the user can ask again */ }
            AuthResult.Success
        } catch (e: FirebaseAuthUserCollisionException) {
            AuthResult.Error("Веќе постои сметка со оваа е-пошта. Користи „Најави се“.")
        } catch (e: Exception) {
            AuthResult.Error(EmailAuthErrors.message(e))
        }
    }

    /**
     * Signs in to an existing email/password account. With nobody signed in it simply signs in. A GUEST is never signed
     * out silently: the credential goes into the same explicit, confirmed switch flow as Google
     * ([signInWithPendingCredential]), which first makes a verified copy of the guest's data.
     */
    suspend fun signInWithEmail(email: String, password: String): AuthResult {
        val auth = auth ?: return notAvailable()
        val user = auth.currentUser
        val credential = EmailAuthProvider.getCredential(email, password)
        return when {
            user == null -> try {
                auth.signInWithCredential(credential).await()
                refresh()
                AuthResult.Success
            } catch (e: Exception) {
                AuthResult.Error(EmailAuthErrors.message(e))
            }
            user.isAnonymous -> {
                pendingConflict = credential
                AuthResult.CredentialInUse(email)
            }
            else -> AuthResult.Error("Веќе си најавен со сметка.")
        }
    }

    /** Sends the "choose a new password" email. Never reveals whether the address has an account. */
    suspend fun sendPasswordReset(email: String): AuthResult {
        val auth = auth ?: return notAvailable()
        return try {
            auth.sendPasswordResetEmail(email).await()
            AuthResult.Success
        } catch (e: FirebaseAuthInvalidUserException) {
            AuthResult.Success    // same answer as for an address that has an account
        } catch (e: Exception) {
            AuthResult.Error(EmailAuthErrors.message(e))
        }
    }

    /** Sends the verification email again for the signed-in email/password account. */
    suspend fun sendVerificationEmail(): AuthResult {
        val user = auth?.currentUser ?: return AuthResult.Error("Не си најавен.")
        return try {
            user.sendEmailVerification().await()
            AuthResult.Success
        } catch (e: Exception) {
            AuthResult.Error(EmailAuthErrors.message(e))
        }
    }

    /**
     * Asks Firebase whether the address has been confirmed since. When it has, a NEW sign-in token is fetched, because the
     * old one still says "not verified" and our server reads that claim. Returns true when the account is verified now.
     */
    suspend fun refreshEmailVerification(): Boolean {
        val user = auth?.currentUser ?: return false
        return try {
            user.reload().await()
            if (user.isEmailVerified) user.getIdToken(true).await()
            refresh()
            user.isEmailVerified
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Proves it is really the owner before an account is deleted (Firebase requires a recent sign-in for that).
     * A guest has nothing to prove; a Google account picks its Google account again; other types are not supported yet.
     */
    suspend fun reauthenticate(activityContext: Context, password: String? = null): AuthResult {
        val user = auth?.currentUser ?: return AuthResult.Error("Не си најавен.")
        if (user.isAnonymous) return AuthResult.Success
        if (kindOf(user) == AccountKind.EMAIL) {
            val email = user.email ?: return AuthResult.Error("Сметката нема е-пошта.")
            if (password.isNullOrEmpty()) return AuthResult.Error("Внеси ја лозинката за да потврдиш.")
            return try {
                user.reauthenticate(EmailAuthProvider.getCredential(email, password)).await()
                AuthResult.Success
            } catch (e: Exception) {
                AuthResult.Error(EmailAuthErrors.message(e))
            }
        }
        if (kindOf(user) != AccountKind.GOOGLE) return AuthResult.Error("Овој тип сметка не може да се избрише од апликацијата.")
        val credential = when (val r = requestGoogleCredential(activityContext)) {
            is GoogleCredentialResult.Ok -> r.credential
            is GoogleCredentialResult.Failed -> return r.result
        }
        return try {
            user.reauthenticate(credential).await()
            AuthResult.Success
        } catch (e: Exception) {
            AuthResult.Error(AuthErrors.message(e))
        }
    }

    /** Deletes the signed-in Firebase account itself. Safe to repeat: with nobody signed in it simply succeeds. */
    suspend fun deleteCurrentAccount(): AuthResult {
        val user = auth?.currentUser ?: return AuthResult.Success
        return try {
            user.delete().await()
            pendingConflict = null
            try {
                CredentialManager.create(appContext).clearCredentialState(ClearCredentialStateRequest())
            } catch (_: Exception) { }
            refresh()
            AuthResult.Success
        } catch (e: Exception) {
            AuthResult.Error(AuthErrors.message(e))
        }
    }

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
    override suspend fun idToken(forceRefresh: Boolean): String? =
        auth?.currentUser?.getIdToken(forceRefresh)?.await()?.token

    /** The id of the signed-in account right now (null when signed out or accounts are unavailable). */
    override val currentUid: String? get() = auth?.currentUser?.uid

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

/** Messages for the email/password forms (wrong password, weak password, ...). */
internal object EmailAuthErrors {
    fun message(e: Exception): String = when (e) {
        is FirebaseNetworkException -> "Нема интернет врска. Провери ја врската и обиди се повторно."
        is FirebaseAuthWeakPasswordException -> "Лозинката е преслаба. Избери подолга лозинка."
        is FirebaseAuthInvalidUserException -> "Погрешна е-пошта или лозинка."
        is FirebaseAuthInvalidCredentialsException -> when ((e as FirebaseAuthException).errorCode) {
            "ERROR_INVALID_EMAIL" -> "Е-поштата не изгледа правилно."
            else -> "Погрешна е-пошта или лозинка."
        }
        is FirebaseAuthException -> when (e.errorCode) {
            "ERROR_INVALID_EMAIL" -> "Е-поштата не изгледа правилно."
            "ERROR_WRONG_PASSWORD", "ERROR_INVALID_CREDENTIAL", "ERROR_USER_NOT_FOUND" -> "Погрешна е-пошта или лозинка."
            "ERROR_WEAK_PASSWORD" -> "Лозинката е преслаба. Избери подолга лозинка."
            "ERROR_EMAIL_ALREADY_IN_USE" -> "Веќе постои сметка со оваа е-пошта. Користи „Најави се“."
            "ERROR_REQUIRES_RECENT_LOGIN" -> "Потврди ја лозинката повторно и обиди се пак."
            "ERROR_TOO_MANY_REQUESTS" -> "Премногу обиди. Почекај малку и обиди се повторно."
            "ERROR_USER_DISABLED" -> "Оваа сметка е оневозможена."
            "ERROR_OPERATION_NOT_ALLOWED" -> "Најавата со е-пошта не е овозможена."
            else -> "Не успеа (${e.errorCode}). Обиди се повторно."
        }
        else -> "Не успеа. Обиди се повторно."
    }
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
