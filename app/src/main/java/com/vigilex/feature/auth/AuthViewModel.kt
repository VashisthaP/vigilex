package com.vigilex.feature.auth

import androidx.activity.ComponentActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.FirebaseException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import com.vigilex.BuildConfig
import com.vigilex.core.data.remote.FirestoreDataSource
import com.vigilex.core.model.Role
import com.vigilex.core.model.User
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.concurrent.TimeUnit
import javax.inject.Inject

// ── UI States ─────────────────────────────────────────────────────────────────
sealed interface LoginUiState {
    /** Checking existing session on app start. */
    object Loading : LoginUiState
    /** No session — show phone number input. */
    object NoSession : LoginUiState
    /** OTP sent — show 6-digit code input. */
    data class OtpSent(val formattedPhone: String) : LoginUiState
    /** Authenticated — navigate to role dashboard. */
    data class Success(val role: Role) : LoginUiState
    /** Error — show message, stay on current step. */
    data class Error(val message: String) : LoginUiState
}

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val auth: FirebaseAuth,
    private val firestore: FirestoreDataSource
) : ViewModel() {

    private val _uiState = MutableStateFlow<LoginUiState>(LoginUiState.Loading)
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    // OTP session state — not persisted, just in-memory
    private var verificationId: String? = null
    private var resendToken: PhoneAuthProvider.ForceResendingToken? = null
    private var lastPhone: String = ""

    init {
        val currentUser = auth.currentUser
        if (currentUser != null) {
            viewModelScope.launch {
                runCatching { resolveRole(currentUser.uid) }
                    .onFailure {
                        // Firestore unreachable / permission denied → go to login
                        auth.signOut()
                        _uiState.value = LoginUiState.NoSession
                    }
            }
        } else {
            _uiState.value = LoginUiState.NoSession
        }
    }

    // ── Phone OTP — Step 1: Send ──────────────────────────────────────────────

    fun sendOtp(rawPhone: String, activity: ComponentActivity) {
        val phone = normalizePhone(rawPhone)
        if (phone.length < 12) {          // +91XXXXXXXXXX = 13 chars minimum
            _uiState.value = LoginUiState.Error("Enter a valid 10-digit phone number")
            return
        }
        lastPhone = phone
        _uiState.value = LoginUiState.Loading

        val callbacks = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
            /** Auto-retrieved (Pixel/some devices) — sign in immediately. */
            override fun onVerificationCompleted(credential: PhoneAuthCredential) {
                viewModelScope.launch {
                    runCatching { signInWithCredential(credential) }
                        .onFailure { _uiState.value = LoginUiState.Error(it.message ?: "Sign-in failed") }
                }
            }

            override fun onVerificationFailed(e: FirebaseException) {
                _uiState.value = LoginUiState.Error(
                    when {
                        e.message?.contains("QUOTA_EXCEEDED") == true ->
                            "SMS quota exceeded. Try again later."
                        e.message?.contains("INVALID_PHONE_NUMBER") == true ->
                            "Invalid phone number. Include country code (e.g. +91XXXXXXXXXX)."
                        else -> e.localizedMessage ?: "Failed to send OTP"
                    }
                )
            }

            override fun onCodeSent(
                verId: String,
                token: PhoneAuthProvider.ForceResendingToken
            ) {
                verificationId = verId
                resendToken = token
                _uiState.value = LoginUiState.OtpSent(phone)
            }
        }

        PhoneAuthProvider.verifyPhoneNumber(
            PhoneAuthOptions.newBuilder(auth)
                .setPhoneNumber(phone)
                .setTimeout(60L, TimeUnit.SECONDS)
                .setActivity(activity)
                .setCallbacks(callbacks)
                .build()
        )
    }

    // ── Phone OTP — Step 2: Verify ────────────────────────────────────────────

    fun verifyOtp(code: String) {
        val vId = verificationId ?: run {
            _uiState.value = LoginUiState.Error("Session expired. Request a new OTP.")
            return
        }
        _uiState.value = LoginUiState.Loading
        viewModelScope.launch {
            runCatching {
                signInWithCredential(PhoneAuthProvider.getCredential(vId, code))
            }.onFailure {
                _uiState.value = LoginUiState.Error("Invalid OTP. Please try again.")
            }
        }
    }

    fun resendOtp(activity: ComponentActivity) {
        val token = resendToken
        if (token == null) { sendOtp(lastPhone, activity); return }

        _uiState.value = LoginUiState.Loading
        val callbacks = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
            override fun onVerificationCompleted(credential: PhoneAuthCredential) {
                viewModelScope.launch {
                    runCatching { signInWithCredential(credential) }
                        .onFailure { _uiState.value = LoginUiState.Error(it.message ?: "Sign-in failed") }
                }
            }
            override fun onVerificationFailed(e: FirebaseException) {
                _uiState.value = LoginUiState.Error(e.localizedMessage ?: "Failed to resend OTP")
            }
            override fun onCodeSent(verId: String, newToken: PhoneAuthProvider.ForceResendingToken) {
                verificationId = verId
                resendToken    = newToken
                _uiState.value = LoginUiState.OtpSent(lastPhone)
            }
        }

        PhoneAuthProvider.verifyPhoneNumber(
            PhoneAuthOptions.newBuilder(auth)
                .setPhoneNumber(lastPhone)
                .setTimeout(60L, TimeUnit.SECONDS)
                .setActivity(activity)
                .setCallbacks(callbacks)
                .setForceResendingToken(token)
                .build()
        )
    }

    // ── Sign out ──────────────────────────────────────────────────────────────

    fun signOut() {
        auth.signOut()
        verificationId = null
        resendToken    = null
        _uiState.value = LoginUiState.NoSession
    }

    /** Reset error back to phone-entry state. */
    fun clearError() {
        if (_uiState.value is LoginUiState.Error) {
            _uiState.value = if (verificationId != null)
                LoginUiState.OtpSent(lastPhone)
            else
                LoginUiState.NoSession
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private suspend fun signInWithCredential(credential: PhoneAuthCredential) {
        val result = auth.signInWithCredential(credential).await()
        val uid    = result.user?.uid ?: error("No UID after sign-in")
        val phone  = auth.currentUser?.phoneNumber ?: lastPhone

        // Seed Super Admin on first phone login if number matches build config
        val superAdminPhone = normalizePhone(BuildConfig.SUPER_ADMIN_PHONE)
        if (phone == superAdminPhone) {
            if (!runCatching { firestore.isSuperAdminSeeded() }.getOrDefault(true)) {
                val sa = User(uid = uid, name = "Super Admin",
                    email = BuildConfig.SUPER_ADMIN_EMAIL,
                    phone = phone, role = Role.SUPER_ADMIN)
                firestore.createUser(sa)
                firestore.seedSuperAdmin(BuildConfig.SUPER_ADMIN_EMAIL, phone, uid)
            }
        }

        // 1. Try direct UID lookup (returning user who already has a phone-auth doc)
        var user = runCatching { firestore.getUser(uid) }.getOrNull()

        if (user == null) {
            // 2. Phone auth created a fresh UID — find existing Firestore doc by phone
            user = runCatching { firestore.getUserByPhone(phone) }.getOrNull()
            if (user != null) {
                // Migrate: create doc at the new Firebase Auth UID
                runCatching { firestore.createUser(user.copy(uid = uid)) }
                user = user.copy(uid = uid)
            }
        }

        _uiState.value = if (user != null) {
            LoginUiState.Success(user.role)
        } else {
            auth.signOut()
            LoginUiState.Error("This phone number is not registered. Contact your administrator.")
        }
    }

    private suspend fun resolveRole(uid: String) {
        val user = firestore.getUser(uid)
        _uiState.value = if (user != null) {
            LoginUiState.Success(user.role)
        } else {
            // No Firestore doc — sign out and ask to log in again
            auth.signOut()
            LoginUiState.NoSession
        }
    }

    /** Normalise a raw phone string to +91XXXXXXXXXX (assumes India if no prefix). */
    private fun normalizePhone(raw: String): String {
        val cleaned = raw.trim().replace(" ", "").replace("-", "")
        return when {
            cleaned.startsWith("+")  -> cleaned
            cleaned.startsWith("0")  -> "+91${cleaned.drop(1)}"
            cleaned.length == 10     -> "+91$cleaned"
            else                     -> "+$cleaned"
        }
    }
}
