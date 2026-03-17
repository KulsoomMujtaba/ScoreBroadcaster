package com.example.scorebroadcaster.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.scorebroadcaster.SupabaseClientProvider
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel for Supabase email/password authentication.
 *
 * Exposes:
 * - [isSessionChecked] — true once the persisted session has been loaded (avoids a flash of the
 *   sign-in screen on cold start for already-authenticated users).
 * - [isAuthenticated] — true when a valid Supabase session is present.
 * - [currentUserEmail] — email of the signed-in user, or null.
 * - [isLoading] — true while an auth network call is in-flight.
 * - [authError] — human-readable error message from the last failed operation, or null.
 */
class AuthViewModel : ViewModel() {

    private val auth get() = SupabaseClientProvider.client.auth

    // True once the initial session-restore check has completed.
    private val _isSessionChecked = MutableStateFlow(false)
    val isSessionChecked: StateFlow<Boolean> = _isSessionChecked.asStateFlow()

    private val _isAuthenticated = MutableStateFlow(false)
    val isAuthenticated: StateFlow<Boolean> = _isAuthenticated.asStateFlow()

    private val _currentUserEmail = MutableStateFlow<String?>(null)
    val currentUserEmail: StateFlow<String?> = _currentUserEmail.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _authError = MutableStateFlow<String?>(null)
    val authError: StateFlow<String?> = _authError.asStateFlow()

    init {
        observeSession()
    }

    /**
     * Collects [auth.sessionStatus] to keep [isAuthenticated] and [isSessionChecked] up to date.
     * The first non-loading status marks the session check as complete.
     */
    private fun observeSession() {
        viewModelScope.launch {
            auth.sessionStatus.collect { status ->
                when (status) {
                    is SessionStatus.Authenticated -> {
                        _isAuthenticated.value = true
                        _currentUserEmail.value = auth.currentUserOrNull()?.email
                        _isSessionChecked.value = true
                    }
                    is SessionStatus.NotAuthenticated -> {
                        _isAuthenticated.value = false
                        _currentUserEmail.value = null
                        _isSessionChecked.value = true
                    }
                    is SessionStatus.RefreshFailure -> {
                        _isAuthenticated.value = false
                        _currentUserEmail.value = null
                        _isSessionChecked.value = true
                    }
                    else -> {
                        // LoadingFromStorage or any future status — keep waiting.
                    }
                }
            }
        }
    }

    fun signIn(email: String, password: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _authError.value = null
            try {
                auth.signInWith(Email) {
                    this.email = email
                    this.password = password
                }
            } catch (e: Exception) {
                _authError.value = mapAuthError(e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun signUp(email: String, password: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _authError.value = null
            try {
                auth.signUpWith(Email) {
                    this.email = email
                    this.password = password
                }
            } catch (e: Exception) {
                _authError.value = mapAuthError(e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun signOut() {
        viewModelScope.launch {
            _isLoading.value = true
            _authError.value = null
            try {
                auth.signOut()
            } catch (e: Exception) {
                _authError.value = mapAuthError(e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun clearError() {
        _authError.value = null
    }

    private fun mapAuthError(e: Exception): String {
        val message = e.message?.lowercase() ?: ""
        return when {
            "invalid" in message && ("email" in message || "password" in message) ||
                    "invalid login credentials" in message ||
                    "credentials" in message ->
                "Invalid email or password."
            "already registered" in message ||
                    "already exists" in message ||
                    "user already" in message ->
                "An account with this email already exists."
            "network" in message ||
                    "timeout" in message ||
                    "connect" in message ->
                "Network error. Please check your connection."
            else -> "Something went wrong. Please try again."
        }
    }
}
