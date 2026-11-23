package com.mojrucek.app.data

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mojrucek.app.repository.LegacyState
import com.mojrucek.app.repository.SharedPreferencesOwnershipPrefs
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The remembered "what happened to the data from before accounts" state. Uses a throw-away preferences file
 * (a separate context name would not reach the real one, so the real keys are saved and put back around each test).
 */
@RunWith(AndroidJUnit4::class)
class OwnershipPrefsInstrumentedTest {

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val raw = context.getSharedPreferences("ownership", Context.MODE_PRIVATE)

    /** Runs [block] with the preferences emptied, then puts back exactly what the real app had stored. */
    private fun isolated(block: () -> Unit) {
        val saved = raw.all.toMap()
        raw.edit().clear().commit()
        try { block() } finally {
            val e = raw.edit().clear()
            for ((k, v) in saved) when (v) { is String -> e.putString(k, v); is Boolean -> e.putBoolean(k, v) }
            e.commit()
        }
    }

    @Test fun startsUnset_andRemembersAnyStateAcrossInstances() = isolated {
        assertEquals(LegacyState.UNSET, SharedPreferencesOwnershipPrefs(context).legacyState)
        for (state in LegacyState.entries) {
            SharedPreferencesOwnershipPrefs(context).legacyState = state
            assertEquals(state, SharedPreferencesOwnershipPrefs(context).legacyState)
        }
    }

    @Test fun anEarlierBuildsClaimedFlag_countsAsDone() = isolated {
        raw.edit().putBoolean("legacy_claim_done", true).commit()
        assertEquals(LegacyState.DONE, SharedPreferencesOwnershipPrefs(context).legacyState)
    }

    @Test fun aNewerSavedStateWinsOverTheOldFlag() = isolated {
        raw.edit().putBoolean("legacy_claim_done", true).putString("legacy_state", "PENDING").commit()
        assertEquals(LegacyState.PENDING, SharedPreferencesOwnershipPrefs(context).legacyState)
    }

    @Test fun anUnreadableSavedState_fallsBackToUnset_neverCrashes() = isolated {
        raw.edit().putString("legacy_state", "SOMETHING_ELSE").commit()
        assertEquals(LegacyState.UNSET, SharedPreferencesOwnershipPrefs(context).legacyState)
    }
}
