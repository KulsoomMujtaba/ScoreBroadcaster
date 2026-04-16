package com.devhub.scored.features.auth.viewmodel
import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.devhub.scored.core.supabase.SupabaseClientProvider
import com.devhub.scored.features.auth.data.AuthErrorMapper
import com.devhub.scored.features.auth.data.ProfileRepository
import com.devhub.scored.features.auth.data.UserProfile
import com.devhub.scored.features.match.data.ScoredDatabase
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Represents the lifecycle state of an in-progress account deletion request. */
enum class DeleteAccountState { IDLE, IN_PROGRESS, SUCCESS, ERROR }

/**
 * ViewModel for Supabase email/password authentication.
 *
 * Exposes:
 * - [isSessionChecked] — true once the persisted session has been loaded (avoids a flash of the
 *   sign-in screen on cold start for already-authenticated users).
 * - [isAuthenticated] — true when a valid Supabase session is present.
 * - [currentUserEmail] — email of the signed-in user, or null.
 * - [currentProfile] — the signed-in user's app-level profile, or null.
 * - [deleteAccountState] — lifecycle state of an in-progress account deletion.
 * - [isLoading] — true while an auth network call is in-flight.
 * - [authError] — human-readable error message from the last failed operation, or null.
 */
class AuthViewModel(application: Application) : AndroidViewModel(application) {

    private val db get() = ScoredDatabase.getInstance(getApplication())

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

    private val _deleteAccountState = MutableStateFlow(DeleteAccountState.IDLE)
    val deleteAccountState: StateFlow<DeleteAccountState> = _deleteAccountState.asStateFlow()

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
            try {
                android.util.Log.d("AuthViewModel", "loadProfile: starting profile sync")
                val upserted = ProfileRepository.upsertProfile()
                if (upserted != null) {
                    // Prefer the server representation if available
                    val server = ProfileRepository.getCurrentProfile() ?: upserted
                    _currentProfile.value = server
                    android.util.Log.d("AuthViewModel", "loadProfile: profile loaded: $server")
                } else {
                    android.util.Log.w("AuthViewModel", "loadProfile: upsert returned null; attempting to fetch existing profile")
                    val server = ProfileRepository.getCurrentProfile()
                    if (server != null) {
                        _currentProfile.value = server
                        android.util.Log.d("AuthViewModel", "loadProfile: fetched existing profile: $server")
                    } else {
                        _authError.value = "Could not load your profile. Please check your connection."
                        android.util.Log.w("AuthViewModel", "loadProfile: no profile found after upsert attempt")
                    }
                }
            } catch (t: Throwable) {
                android.util.Log.e("AuthViewModel", "loadProfile failed", t)
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

    /**
     * Permanently deletes the signed-in user's account and all associated data.
     *
     * Deletion order:
     *  1. All backend user data is removed via the `delete_account` Supabase RPC
     *     (team_players → teams → players → match_events → matches → profiles →
     *     auth.users).  The server-side function is SECURITY DEFINER so it can
     *     delete from the protected auth.users table while still being scoped to
     *     the calling user via `auth.uid()`.
     *  2. The local Room database is cleared so no residual data remains on device.
     *  3. The in-memory session is invalidated (sign-out).
     *
     * Outcomes are published on [deleteAccountState].  Callers should observe
     * [DeleteAccountState.SUCCESS] to navigate to the login screen and
     * [DeleteAccountState.ERROR] to surface an error message.
     */
    fun deleteAccount() {
        val authClient = auth
        if (authClient == null) {
            _authError.value = SupabaseClientProvider.missingConfigMessage
            return
        }

        viewModelScope.launch {
            _isLoading.value = true
            _authError.value = null
            _deleteAccountState.value = DeleteAccountState.IN_PROGRESS
            Log.d("AuthViewModel", "Delete account initiated")

            try {
                val supabase = SupabaseClientProvider.clientOrNull
                if (supabase != null) {
                    // Step 1: Delete all user data + auth account via SECURITY DEFINER RPC.
                    supabase.postgrest.rpc("delete_account")
                    Log.d("AuthViewModel", "User data deletion completed")
                    Log.d("AuthViewModel", "Auth account deleted")
                }

                // Step 2: Clear the local Room database.
                try {
                    db.clearAllTables()
                } catch (dbEx: Exception) {
                    // Remote deletion already succeeded. Log the local cleanup failure so
                    // the user is still considered deleted, but the failure is surfaced for
                    // diagnostics.  The account no longer exists on the server regardless.
                    Log.w("AuthViewModel", "clearAllTables failed after successful remote deletion", dbEx)
                }

                _deleteAccountState.value = DeleteAccountState.SUCCESS

                // Step 3: Invalidate the local session (best-effort; the auth row is
                // already gone so the session tokens are invalid regardless).
                try {
                    authClient.signOut()
                } catch (signOutEx: Exception) {
                    // Session is already invalidated on the server — safe to ignore, but
                    // log at debug level to aid troubleshooting.
                    Log.d("AuthViewModel", "signOut after deletion threw (expected if session already gone)", signOutEx)
                }
            } catch (e: Exception) {
                Log.e("AuthViewModel", "Delete account failed", e)
                _authError.value = "Failed to delete account. Please try again."
                _deleteAccountState.value = DeleteAccountState.ERROR
            } finally {
                _isLoading.value = false
            }
        }
    }

    /** Resets [deleteAccountState] to [DeleteAccountState.IDLE]. */
    fun clearDeleteAccountState() {
        _deleteAccountState.value = DeleteAccountState.IDLE
    }
}
