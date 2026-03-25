package com.example.scorebroadcaster.features.auth.viewmodel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.scorebroadcaster.core.supabase.SupabaseClientProvider
import com.example.scorebroadcaster.features.auth.data.AuthErrorMapper
import com.example.scorebroadcaster.features.auth.data.ProfileRepository
import com.example.scorebroadcaster.features.auth.data.UserProfile
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
 * - [currentProfile] — the signed-in user's app-level profile, or null.
 * - [isLoading] — true while an auth network call is in-flight.
 * - [authError] — human-readable error message from the last failed operation, or null.
 */
class AuthViewModel : ViewModel() {

    private val auth get() = SupabaseClientProvider.clientOrNull?.auth

    // True once the initial session-restore check has completed.
    private val _isSessionChecked = MutableStateFlow(false)
    val isSessionChecked: StateFlow<Boolean> = _isSessionChecked.asStateFlow()

    private val _isAuthenticated = MutableStateFlow(false)
    val isAuthenticated: StateFlow<Boolean> = _isAuthenticated.asStateFlow()

    private val _currentUserEmail = MutableStateFlow<String?>(null)
    val currentUserEmail: StateFlow<String?> = _currentUserEmail.asStateFlow()

    private val _currentProfile = MutableStateFlow<UserProfile?>(null)
    val currentProfile: StateFlow<UserProfile?> = _currentProfile.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _authError = MutableStateFlow<String?>(null)
    val authError: StateFlow<String?> = _authError.asStateFlow()

    private val _resetSuccess = MutableStateFlow(false)
    val resetSuccess: StateFlow<Boolean> = _resetSuccess.asStateFlow()

    init {
        observeSession()
    }

    /**
     * Collects Supabase session status to keep [isAuthenticated] and [isSessionChecked] up to date.
     * The first non-loading status marks the session check as complete.
     *
     * When a session becomes [SessionStatus.Authenticated] the profile is automatically
     * upserted (created on first sign-in, refreshed on subsequent logins / session restores).
     */
    private fun observeSession() {
        val authClient = auth
        if (authClient == null) {
            _authError.value = SupabaseClientProvider.missingConfigMessage
            _isSessionChecked.value = true
            return
        }

        viewModelScope.launch {
            authClient.sessionStatus.collect { status ->
                when (status) {
                    is SessionStatus.Authenticated -> {
                        _isAuthenticated.value = true
                        _currentUserEmail.value = authClient.currentUserOrNull()?.email
                        _isSessionChecked.value = true
                        loadProfile()
                    }
                    is SessionStatus.NotAuthenticated -> {
                        _isAuthenticated.value = false
                        _currentUserEmail.value = null
                        _currentProfile.value = null
                        _isSessionChecked.value = true
                    }
                    is SessionStatus.RefreshFailure -> {
                        _isAuthenticated.value = false
                        _currentUserEmail.value = null
                        _currentProfile.value = null
                        _isSessionChecked.value = true
                    }
                    else -> {
                        // LoadingFromStorage or any future status — keep waiting.
                    }
                }
            }
        }
    }

    /**
     * Upserts the profile row for the currently signed-in user, then caches the
     * result in [currentProfile].  Safe to call multiple times — the upsert is
     * idempotent and will not create duplicates.
     *
     * Failures are surfaced through [authError] so callers are aware of backend
     * issues (e.g. network outage, misconfigured Supabase keys).
     */
    private fun loadProfile() {
        viewModelScope.launch {
            val profile = ProfileRepository.upsertProfile()
            if (profile != null) {
                _currentProfile.value = profile
            } else {
                _authError.value = "Could not load your profile. Please check your connection."
            }
        }
    }

    fun signIn(email: String, password: String) {
        val authClient = auth
        if (authClient == null) {
            _authError.value = SupabaseClientProvider.missingConfigMessage
            return
        }

        viewModelScope.launch {
            _isLoading.value = true
            _authError.value = null
            try {
                authClient.signInWith(Email) {
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
        val authClient = auth
        if (authClient == null) {
            _authError.value = SupabaseClientProvider.missingConfigMessage
            return
        }

        viewModelScope.launch {
            _isLoading.value = true
            _authError.value = null
            try {
                authClient.signUpWith(Email) {
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
        val authClient = auth
        if (authClient == null) {
            _authError.value = SupabaseClientProvider.missingConfigMessage
            return
        }

        viewModelScope.launch {
            _isLoading.value = true
            _authError.value = null
            try {
                authClient.signOut()
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

    fun clearResetSuccess() {
        _resetSuccess.value = false
    }

    fun sendPasswordResetEmail(email: String) {
        val authClient = auth
        if (authClient == null) {
            _authError.value = SupabaseClientProvider.missingConfigMessage
            return
        }

        viewModelScope.launch {
            _isLoading.value = true
            _authError.value = null
            _resetSuccess.value = false
            try {
                authClient.resetPasswordForEmail(email)
                _resetSuccess.value = true
            } catch (e: Exception) {
                _authError.value = AuthErrorMapper.map(e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    private fun mapAuthError(e: Exception): String = AuthErrorMapper.map(e)
}
