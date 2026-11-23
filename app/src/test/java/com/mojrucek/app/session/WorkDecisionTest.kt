package com.mojrucek.app.session

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkDecisionTest {

    private val retry = RefreshOutcome.Retry("no connection")
    private val stored = StoredSession("usr_1", "GUEST", "GUEST", null, null, 0, 0)

    @Test fun temporaryTrouble_isRetried_untilTheLimit() {
        for (attempt in 0 until WorkDecision.MAX_ATTEMPTS) assertTrue("attempt $attempt", WorkDecision.shouldRetry(retry, attempt))
        assertFalse(WorkDecision.shouldRetry(retry, WorkDecision.MAX_ATTEMPTS))
        assertFalse(WorkDecision.shouldRetry(retry, WorkDecision.MAX_ATTEMPTS + 50))
    }

    @Test fun anythingElse_endsTheJob_atEveryAttempt() {
        val others = listOf(RefreshOutcome.Updated(stored), RefreshOutcome.Fresh, RefreshOutcome.GaveUp("refused"), RefreshOutcome.NoToken, RefreshOutcome.AccountChanged)
        for (outcome in others) for (attempt in listOf(0, 1, 7, 8, 100)) assertFalse("$outcome at $attempt", WorkDecision.shouldRetry(outcome, attempt))
    }

    @Test fun theLimitIsReasonable_soTheJobDoesNotRetryForever() {
        assertEquals(8, WorkDecision.MAX_ATTEMPTS)
    }
}
