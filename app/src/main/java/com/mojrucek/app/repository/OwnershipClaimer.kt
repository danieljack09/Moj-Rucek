package com.mojrucek.app.repository

import android.content.Context
import androidx.room.withTransaction
import com.mojrucek.app.data.AppDatabase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * What happened to the data from before accounts owned data (rows with no owner), per phone:
 *  - [UNSET]   not looked at yet;
 *  - [PENDING] nobody was signed in when it was first looked at, so the next account that signs in is ASKED;
 *  - [DONE]    handed to an account, or there was nothing to hand over. Never asked again.
 */
enum class LegacyState { UNSET, PENDING, DONE }

/** Remembers the [LegacyState] on this phone. */
interface OwnershipPrefs {
    var legacyState: LegacyState
}

class SharedPreferencesOwnershipPrefs(context: Context) : OwnershipPrefs {
    private val prefs = context.applicationContext.getSharedPreferences("ownership", Context.MODE_PRIVATE)

    override var legacyState: LegacyState
        get() {
            prefs.getString(STATE_KEY, null)?.let { saved -> LegacyState.entries.firstOrNull { it.name == saved }?.let { return it } }
            // An earlier build only remembered "claimed or not".
            return if (prefs.getBoolean(OLD_KEY, false)) LegacyState.DONE else LegacyState.UNSET
        }
        set(value) { prefs.edit().putString(STATE_KEY, value.name).apply() }

    private companion object {
        const val STATE_KEY = "legacy_state"
        const val OLD_KEY = "legacy_claim_done"
    }
}

class InMemoryOwnershipPrefs(override var legacyState: LegacyState = LegacyState.UNSET) : OwnershipPrefs

/** The data that has no owner yet, as far as the decision logic needs to know. */
interface UnownedData {
    suspend fun count(): Int

    /** Gives all of it to [uid]; returns how many rows moved. */
    suspend fun claimFor(uid: String): Int
}

/**
 * Hands data without an owner (ownerUid = "") to an account. Only rows with NO owner are touched; another
 * account's rows never are. Whether and when to do it is decided by [OwnershipCoordinator].
 */
class OwnershipClaimer(private val database: AppDatabase) : UnownedData {

    override suspend fun count(): Int = database.ownershipDao().countUnowned()

    override suspend fun claimFor(uid: String): Int {
        if (!OwnerKeys.isAccount(uid)) return 0
        return database.withTransaction {
            val dao = database.ownershipDao()
            dao.claimIngredients(uid) + dao.claimShoppingItems(uid) + dao.claimWeeklyPlans(uid) +
                dao.claimOwnRecipes(uid) + dao.claimFavorites(uid)
        }
    }
}

/**
 * Decides what happens to the data from before accounts. Called whenever the current owner changes (and once at start):
 *  - first look, an account IS signed in  -> it gets the data, no question (the person using the app is the owner);
 *  - first look, nobody signed in         -> if there is such data, remember to ASK at the next sign-in ([LegacyState.PENDING]);
 *  - pending, an account signs in         -> [promptNeeded] turns true: the app asks "keep it in this account?";
 *  - [keep] -> the account gets the data, never asked again; [askLater] -> nothing changes, asked again next time.
 * A build without accounts (owner = unowned) never claims and never asks. Nothing is ever deleted or uploaded here.
 */
class OwnershipCoordinator(
    private val unowned: UnownedData,
    private val prefs: OwnershipPrefs,
    /** Runs after data was handed to an account (tidy-ups that need the data to be owned first). */
    private val afterClaim: suspend () -> Unit = {}
) {
    private val _promptNeeded = MutableStateFlow(false)

    /** True while the "keep the data from before accounts in this account?" question should be on screen. */
    val promptNeeded: StateFlow<Boolean> = _promptNeeded.asStateFlow()

    suspend fun onOwnerChanged(owner: String) {
        _promptNeeded.value = false
        when (prefs.legacyState) {
            LegacyState.DONE -> return
            LegacyState.UNSET -> when {
                OwnerKeys.isAccount(owner) -> claim(owner)
                owner == OwnerKeys.SIGNED_OUT -> prefs.legacyState = if (unowned.count() > 0) LegacyState.PENDING else LegacyState.DONE
                else -> Unit   // a build without accounts: nothing to decide
            }
            LegacyState.PENDING -> if (OwnerKeys.isAccount(owner)) {
                if (unowned.count() > 0) _promptNeeded.value = true else prefs.legacyState = LegacyState.DONE
            }
        }
    }

    /** The user chose "keep it in this account". Does nothing unless the question is really pending. */
    suspend fun keep(owner: String) {
        if (prefs.legacyState != LegacyState.PENDING || !OwnerKeys.isAccount(owner)) return
        claim(owner)
        _promptNeeded.value = false
    }

    /** The user chose "later": nothing changes, the question comes back at the next sign-in or start. */
    fun askLater() {
        _promptNeeded.value = false
    }

    private suspend fun claim(owner: String) {
        unowned.claimFor(owner)
        // Only after the claim succeeded: a failure leaves the data unowned and the state as it was.
        prefs.legacyState = LegacyState.DONE
        afterClaim()
    }
}
