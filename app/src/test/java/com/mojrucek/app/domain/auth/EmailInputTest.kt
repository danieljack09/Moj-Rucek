package com.mojrucek.app.domain.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class EmailInputTest {

    @Test fun normalAddresses_areAccepted() {
        for (e in listOf("a@b.mk", "ime.prezime@gmail.com", "x+tag@sub.domain.org", "  spaced@mail.com  ", "a_b-c@d-e.co.uk")) {
            assertNull(e, EmailInput.emailProblem(e))
        }
    }

    @Test fun surroundingSpaces_areRemoved() {
        assertEquals("a@b.mk", EmailInput.normalize("  a@b.mk \n"))
    }

    @Test fun badAddresses_areRefused() {
        for (e in listOf("", "   ", "plain", "@b.mk", "a@", "a@b", "a@@b.mk", "a@b@c.mk", "a b@c.mk", "a@b .mk", "a@.mk", "a@b.", "a@b..mk", "a@x", "x".repeat(250) + "@b.mk")) {
            assertNotNull("'$e' should be refused", EmailInput.emailProblem(e))
        }
    }

    @Test fun newPasswords_needEightCharacters_andNotJustSpaces() {
        assertNotNull(EmailInput.newPasswordProblem(""))
        assertNotNull(EmailInput.newPasswordProblem("1234567"))
        assertNull(EmailInput.newPasswordProblem("12345678"))
        assertNull(EmailInput.newPasswordProblem("a".repeat(128)))
        assertNotNull(EmailInput.newPasswordProblem("a".repeat(129)))
        assertNotNull(EmailInput.newPasswordProblem("        "))
        assertNull("a space inside is fine", EmailInput.newPasswordProblem("моја лозинка 1"))
    }

    @Test fun signingIn_onlyNeedsSomethingTyped() {
        assertNull(EmailInput.passwordTypedProblem("x"))
        assertNotNull(EmailInput.passwordTypedProblem(""))
        assertNull(EmailInput.signInProblem("a@b.mk", "short"))
    }

    @Test fun theEmailIsCheckedBeforeThePassword() {
        assertEquals(EmailInput.emailProblem("bad"), EmailInput.createProblem("bad", "x"))
        assertEquals(EmailInput.newPasswordProblem("x"), EmailInput.createProblem("a@b.mk", "x"))
        assertNull(EmailInput.createProblem("a@b.mk", "12345678"))
        assertEquals(EmailInput.emailProblem("bad"), EmailInput.signInProblem("bad", ""))
    }
}
