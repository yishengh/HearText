package com.yishenghuang.heartext.ui.auth

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.clerk.api.Clerk
import com.clerk.api.auth.types.MfaType
import com.clerk.api.auth.types.VerificationType
import com.clerk.api.network.serialization.onFailure
import com.clerk.api.network.serialization.onSuccess
import com.clerk.api.signin.SignIn
import com.clerk.api.signin.sendMfaEmailCode
import com.clerk.api.signin.verifyMfaCode
import com.clerk.api.signup.SignUp
import com.clerk.api.signup.sendCode
import com.clerk.api.signup.verifyCode
import com.yishenghuang.heartext.BuildConfig
import com.yishenghuang.heartext.data.BookRepository
import com.yishenghuang.heartext.network.message
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

sealed interface AuthUiState {
    data object Loading : AuthUiState
    data object SignedOut : AuthUiState
    data object SignedIn : AuthUiState
    data object MissingClerkKey : AuthUiState
}

class AuthViewModel(
    private val bookRepository: BookRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(
        when {
            BuildConfig.CLERK_PUBLISHABLE_KEY.isBlank() -> AuthUiState.MissingClerkKey
            // Avoid login flash after rotation if Clerk already has a session.
            Clerk.userFlow.value != null -> AuthUiState.SignedIn
            else -> AuthUiState.Loading
        }
    )
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private val _meLabel = MutableStateFlow<String?>(null)
    val meLabel: StateFlow<String?> = _meLabel.asStateFlow()

    init {
        if (BuildConfig.CLERK_PUBLISHABLE_KEY.isNotBlank()) {
            viewModelScope.launch {
                combine(Clerk.isInitialized, Clerk.userFlow) { ready, user ->
                    ready to user
                }.collect { (ready, user) ->
                    val next = when {
                        // Stay on current screen while Clerk boots (do not dump signed-in users to login).
                        !ready -> when (_uiState.value) {
                            AuthUiState.SignedIn -> AuthUiState.SignedIn
                            else -> AuthUiState.Loading
                        }
                        user != null -> AuthUiState.SignedIn
                        else -> AuthUiState.SignedOut
                    }
                    val wasSignedIn = _uiState.value is AuthUiState.SignedIn
                    _uiState.value = next
                    if (next is AuthUiState.SignedIn) {
                        _meLabel.value = user?.id ?: Clerk.userFlow.value?.id
                        if (!wasSignedIn) {
                            // Do not block auth state collection on sync.
                            viewModelScope.launch {
                                runCatching { bookRepository.ensureSampleBooks() }
                                runCatching { bookRepository.syncOnLogin() }
                                    .onFailure { _message.value = "Sync: ${it.message}" }
                            }
                        }
                    } else if (next !is AuthUiState.Loading) {
                        _meLabel.value = null
                    }
                }
            }
        }
    }

    fun clearMessage() {
        _message.value = null
    }

    fun signOut() {
        viewModelScope.launch {
            Clerk.auth.signOut()
                .onSuccess { _uiState.value = AuthUiState.SignedOut }
                .onFailure { failure ->
                    _message.value = failure.message()
                    Log.e("AuthViewModel", failure.message(), failure.throwable)
                }
        }
    }
}

class SignInViewModel : ViewModel() {
    sealed interface State {
        data object Form : State
        data object NeedsClientTrust : State
    }

    private val _state = MutableStateFlow<State>(State.Form)
    val state: StateFlow<State> = _state.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()
    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    fun signIn(email: String, password: String) {
        viewModelScope.launch {
            _busy.value = true
            _error.value = null
            Clerk.auth
                .signInWithPassword {
                    identifier = email.trim()
                    this.password = password
                }
                .onSuccess { signIn -> handleSignInResult(signIn) }
                .onFailure { failure ->
                    _busy.value = false
                    _error.value = failure.message()
                }
        }
    }

    fun verifyClientTrust(code: String) {
        val signIn = Clerk.auth.currentSignIn ?: return
        viewModelScope.launch {
            _busy.value = true
            _error.value = null
            signIn
                .verifyMfaCode(code.trim(), MfaType.EMAIL_CODE)
                .onSuccess { updated -> handleSignInResult(updated) }
                .onFailure { failure ->
                    _busy.value = false
                    _error.value = failure.message()
                }
        }
    }

    fun resendClientTrustCode() {
        val signIn = Clerk.auth.currentSignIn ?: return
        viewModelScope.launch {
            _busy.value = true
            _error.value = null
            signIn
                .sendMfaEmailCode()
                .onSuccess {
                    _busy.value = false
                    _error.value = null
                }
                .onFailure { failure ->
                    _busy.value = false
                    _error.value = failure.message()
                }
        }
    }

    private suspend fun handleSignInResult(signIn: SignIn) {
        when (signIn.status) {
            SignIn.Status.COMPLETE -> {
                activateSessionIfNeeded(signIn)
                _busy.value = false
                _state.value = State.Form
            }
            SignIn.Status.NEEDS_CLIENT_TRUST,
            SignIn.Status.NEEDS_SECOND_FACTOR -> {
                val emailFactor = signIn.supportedSecondFactors
                    .orEmpty()
                    .firstOrNull { it.strategy == "email_code" }
                if (emailFactor == null) {
                    _busy.value = false
                    _error.value = "Sign-in needs extra verification that this app does not support yet."
                    Log.e("SignInViewModel", "Unsupported second factor: ${signIn.status}")
                    return
                }
                signIn
                    .sendMfaEmailCode(emailAddressId = emailFactor.emailAddressId)
                    .onSuccess {
                        _state.value = State.NeedsClientTrust
                        _busy.value = false
                    }
                    .onFailure { failure ->
                        _busy.value = false
                        _error.value = failure.message()
                    }
            }
            else -> {
                _busy.value = false
                _error.value = "Sign-in incomplete (${signIn.status})."
                Log.e("SignInViewModel", "Sign-in attempt not complete: ${signIn.status}")
            }
        }
    }

    private suspend fun activateSessionIfNeeded(signIn: SignIn) {
        val sessionId = signIn.createdSessionId ?: return
        if (Clerk.session?.id == sessionId) return
        Clerk.auth.setActive(sessionId)
            .onFailure { failure ->
                Log.e("SignInViewModel", "setActive failed: ${failure.message()}", failure.throwable)
                _error.value = failure.message()
            }
    }
}

class SignUpViewModel : ViewModel() {
    sealed interface State {
        data object Form : State
        data object NeedsVerification : State
        data object Done : State
    }

    private val _state = MutableStateFlow<State>(State.Form)
    val state: StateFlow<State> = _state.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()
    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private var pendingEmail: String = ""

    fun signUp(email: String, password: String) {
        viewModelScope.launch {
            _busy.value = true
            _error.value = null
            pendingEmail = email.trim()
            Clerk.auth
                .signUp {
                    this.email = pendingEmail
                    this.password = password
                }
                .onSuccess { signUp ->
                    if (signUp.status == SignUp.Status.COMPLETE) {
                        activateSessionIfNeeded(signUp)
                        _state.value = State.Done
                        _busy.value = false
                    } else {
                        signUp
                            .sendCode { this.email = pendingEmail }
                            .onSuccess {
                                _state.value = State.NeedsVerification
                                _busy.value = false
                            }
                            .onFailure { failure ->
                                _busy.value = false
                                _error.value = failure.message()
                            }
                    }
                }
                .onFailure { failure ->
                    _busy.value = false
                    _error.value = failure.message()
                }
        }
    }

    fun verify(code: String) {
        val inProgress = Clerk.auth.currentSignUp ?: return
        viewModelScope.launch {
            _busy.value = true
            inProgress
                .verifyCode(code.trim(), VerificationType.EMAIL)
                .onSuccess { signUp ->
                    if (signUp.status == SignUp.Status.COMPLETE) {
                        activateSessionIfNeeded(signUp)
                    }
                    _state.value = State.Done
                    _busy.value = false
                }
                .onFailure { failure ->
                    _busy.value = false
                    _error.value = failure.message()
                }
        }
    }

    private suspend fun activateSessionIfNeeded(signUp: SignUp) {
        val sessionId = signUp.createdSessionId ?: return
        if (Clerk.session?.id == sessionId) return
        Clerk.auth.setActive(sessionId)
            .onFailure { failure ->
                Log.e("SignUpViewModel", "setActive failed: ${failure.message()}", failure.throwable)
                _error.value = failure.message()
            }
    }
}
