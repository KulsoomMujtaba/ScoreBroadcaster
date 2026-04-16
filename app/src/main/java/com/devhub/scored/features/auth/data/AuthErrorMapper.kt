package com.devhub.scored.features.auth.data

/**
 * Converts raw Supabase / auth exceptions into concise, user-friendly messages.
 *
 * All matching is case-insensitive on [Exception.message].  Unknown errors fall
 * back to a generic "Something went wrong" string so that raw stack-trace text
 * is never surfaced in the UI.
 */
object AuthErrorMapper {

    fun map(e: Exception): String {
        val msg = e.message?.lowercase() ?: ""
        return when {
            // ── Incorrect credentials ─────────────────────────────────────────
            "invalid login credentials" in msg ||
                    "invalid" in msg && ("email" in msg || "password" in msg) ||
                    "credentials" in msg ->
                "Incorrect email or password."

            // ── Existing account ──────────────────────────────────────────────
            "already registered" in msg ||
                    "already exists" in msg ||
                    "user already" in msg ->
                "An account with this email already exists."

            // ── Weak / invalid password ───────────────────────────────────────
            "password" in msg && ("weak" in msg || "too short" in msg || "strength" in msg) ->
                "Please choose a stronger password."

            // ── Email not confirmed ───────────────────────────────────────────
            "email not confirmed" in msg ||
                    "not confirmed" in msg ||
                    "confirm your email" in msg ->
                "Please verify your email before signing in."

            // ── Invalid email format ──────────────────────────────────────────
            "invalid email" in msg || "email address" in msg && "invalid" in msg ->
                "Please enter a valid email address."

            // ── Network / connectivity ────────────────────────────────────────
            "network" in msg ||
                    "timeout" in msg ||
                    "unable to resolve" in msg ||
                    "no internet" in msg ||
                    "connect" in msg ->
                "No internet connection. Please try again."

            // ── Fallback ──────────────────────────────────────────────────────
            else -> "Something went wrong. Please try again."
        }
    }
}
