package com.mojrucek.app.repository

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OwnerKeysTest {

    @Test fun aSignedInAccount_isItsFirebaseUid() {
        assertEquals("abc123", OwnerKeys.of(accountsAvailable = true, uid = "abc123"))
    }

    @Test fun nobodySignedIn_isNeverAnAccountsKey_andNeverTheUnownedBucket() {
        val key = OwnerKeys.of(accountsAvailable = true, uid = null)
        assertEquals(OwnerKeys.SIGNED_OUT, key)
        assertNotEquals("nobody must not see the unowned legacy data", OwnerKeys.UNOWNED, key)
        assertEquals(OwnerKeys.SIGNED_OUT, OwnerKeys.of(true, ""))
    }

    @Test fun aBuildWithoutAccounts_usesTheUnownedBucket() {
        assertEquals(OwnerKeys.UNOWNED, OwnerKeys.of(accountsAvailable = false, uid = null))
        assertEquals(OwnerKeys.UNOWNED, OwnerKeys.of(accountsAvailable = false, uid = "ignored"))
    }

    @Test fun onlyARealAccount_countsAsAnAccount() {
        assertTrue(OwnerKeys.isAccount("uid-1"))
        assertFalse(OwnerKeys.isAccount(OwnerKeys.UNOWNED))
        assertFalse(OwnerKeys.isAccount(OwnerKeys.SIGNED_OUT))
    }

    @Test fun theSignedOutKey_cannotBeAFirebaseUid() {
        // Firebase uids are 28 plain letters/digits; the marker contains characters they never have.
        assertFalse(OwnerKeys.SIGNED_OUT.all { it.isLetterOrDigit() })
    }

    @Test fun currentOwner_followsTheAccountAsItChanges() = runBlocking {
        val keys = MutableStateFlow("uid-A")
        val owner = CurrentOwner.following(keys)
        assertEquals("uid-A", owner.key)
        assertEquals("uid-A", owner.flow.first())
        keys.value = "uid-B"
        assertEquals("uid-B", owner.key)
        assertEquals("uid-B", owner.flow.first())
        keys.value = OwnerKeys.SIGNED_OUT
        assertEquals(OwnerKeys.SIGNED_OUT, owner.key)
    }
}
