package com.mojrucek.app.domain.auth

/** What was typed in the email form, checked before anything is sent anywhere. */
object EmailInput {
    const val MIN_PASSWORD = 8
    const val MAX_PASSWORD = 128
    private const val MAX_EMAIL = 254

    /** Surrounding spaces are never part of an address (a keyboard often adds one). */
    fun normalize(email: String): String = email.trim()

    /** A simple shape check: one @, something before it, a dotted domain after it, no spaces. The server of Firebase decides the rest. */
    fun emailProblem(raw: String): String? {
        val email = normalize(raw)
        if (email.isEmpty()) return "Внеси е-пошта."
        if (email.length > MAX_EMAIL || email.any { it.isWhitespace() }) return "Е-поштата не изгледа правилно."
        val at = email.indexOf('@')
        if (at <= 0 || at != email.lastIndexOf('@')) return "Е-поштата не изгледа правилно."
        val domain = email.substring(at + 1)
        if (domain.length < 3 || !domain.contains('.') || domain.startsWith('.') || domain.endsWith('.') || domain.contains("..")) {
            return "Е-поштата не изгледа правилно."
        }
        return null
    }

    /** Only for creating an account (signing in accepts whatever the account has). */
    fun newPasswordProblem(password: String): String? = when {
        password.length < MIN_PASSWORD -> "Лозинката мора да има најмалку $MIN_PASSWORD знаци."
        password.length > MAX_PASSWORD -> "Лозинката е предолга."
        password.isBlank() -> "Лозинката не може да биде само празни места."
        else -> null
    }

    /** For signing in: only that something was typed. */
    fun passwordTypedProblem(password: String): String? = if (password.isEmpty()) "Внеси лозинка." else null

    fun createProblem(email: String, password: String): String? = emailProblem(email) ?: newPasswordProblem(password)
    fun signInProblem(email: String, password: String): String? = emailProblem(email) ?: passwordTypedProblem(password)
}
