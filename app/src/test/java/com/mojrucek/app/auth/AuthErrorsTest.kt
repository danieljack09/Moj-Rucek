package com.mojrucek.app.auth

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Firebase's own exception types (FirebaseNetworkException, FirebaseAuthException) cannot be built in
 * a plain JVM test (they call Android stubs), so the offline message is verified on a device instead.
 */
class AuthErrorsTest {

    @Test fun unknownFailure_getsAGenericRetryMessage() {
        assertEquals("Најавата не успеа. Обиди се повторно.", AuthErrors.message(IllegalStateException("x")))
    }
}
