package com.example.scorebroadcaster.features.auth.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.scorebroadcaster.features.auth.viewmodel.AuthViewModel
import com.example.scorebroadcaster.ui.isValidEmailAddress

/**
 * Forgot-password screen.
 *
 * Lets the user enter their email address and request a Supabase password-reset
 * link.  Displays a success message on send and an error message on failure.
 * Loading state disables the button while the request is in-flight.
 */
@Composable
fun ForgotPasswordScreen(
    authViewModel: AuthViewModel = viewModel(),
    onNavigateToSignIn: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isLoading by authViewModel.isLoading.collectAsStateWithLifecycle()
    val authError by authViewModel.authError.collectAsStateWithLifecycle()
    val resetSuccess by authViewModel.resetSuccess.collectAsStateWithLifecycle()

    var email by remember { mutableStateOf("") }
    var emailError by remember { mutableStateOf<String?>(null) }

    // Clear transient VM state when leaving the screen.
    DisposableEffect(Unit) {
        onDispose {
            authViewModel.clearError()
            authViewModel.clearResetSuccess()
        }
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 32.dp, vertical = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Reset Password",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Enter your email address and we'll send you a link to reset your password.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(32.dp))

            OutlinedTextField(
                value = email,
                onValueChange = {
                    email = it
                    val trimmed = it.trim()
                    emailError = when {
                        trimmed.isEmpty() -> null
                        !isValidEmailAddress(trimmed) -> "Enter a valid email address"
                        else -> null
                    }
                    authViewModel.clearError()
                    authViewModel.clearResetSuccess()
                },
                label = { Text("Email") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                isError = emailError != null,
                supportingText = emailError?.let { err -> { Text(err) } },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isLoading && !resetSuccess
            )
            Spacer(modifier = Modifier.height(8.dp))

            // ── Error message ─────────────────────────────────────────────────
            authError?.let { error ->
                Text(
                    text = error,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            // ── Success message ───────────────────────────────────────────────
            if (resetSuccess) {
                Text(
                    text = "Password reset link sent. Check your email.",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            Button(
                onClick = {
                    val trimmed = email.trim()
                    emailError = when {
                        trimmed.isBlank() -> "Email is required"
                        !isValidEmailAddress(trimmed) -> "Enter a valid email address"
                        else -> null
                    }
                    if (emailError == null) {
                        authViewModel.sendPasswordResetEmail(trimmed)
                    }
                },
                enabled = !isLoading && !resetSuccess,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text("Send Reset Link")
                }
            }
            Spacer(modifier = Modifier.height(4.dp))

            TextButton(onClick = onNavigateToSignIn) {
                Text("Back to Sign In")
            }
        }
    }
}
