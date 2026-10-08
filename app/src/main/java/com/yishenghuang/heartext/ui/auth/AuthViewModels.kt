package com.yishenghuang.heartext.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yishenghuang.heartext.BuildConfig
import com.yishenghuang.heartext.R
import com.yishenghuang.heartext.data.BookRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
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
    data object ConnectionFailed : AuthUiState
}

class AuthViewModel(
    private val synchronize: suspend () -> Unit,
    private val source: AuthSessionSource = ClerkSessionSource,
    private val configured: Boolean = BuildConfig.CLERK_PUBLISHABLE_KEY.isNotBlank(),
    syncEnabled: Boolean = true
) : ViewModel() {
    constructor(bookRepository: BookRepository, syncEnabled: Boolean = true) : this(synchronize = {
        bookRepository.ensureSampleBooks()
        bookRepository.syncOnLogin()
    }, syncEnabled = syncEnabled)
    private fun stateFor(snapshot: AuthSnapshot): AuthUiState = when {
        snapshot.ready && snapshot.userId != null -> AuthUiState.SignedIn
        snapshot.ready -> AuthUiState.SignedOut
        snapshot.initializationFailed -> AuthUiState.ConnectionFailed
        snapshot.userId != null -> AuthUiState.SignedIn
        else -> AuthUiState.Loading
    }
    private val _uiState = MutableStateFlow(if (configured) stateFor(source.initial) else AuthUiState.MissingClerkKey)
    val uiState = _uiState.asStateFlow()
    private val _message = MutableStateFlow<Int?>(null)
    val message = _message.asStateFlow()
    private val _meLabel = MutableStateFlow<String?>(null)
    val meLabel = _meLabel.asStateFlow()
    private var syncKey: String? = null
    private var syncJob: Job? = null
    private var signingOut = false
    private val syncAllowed = MutableStateFlow(syncEnabled)

    init {
        if (configured) viewModelScope.launch {
            combine(source.states, syncAllowed) { snapshot, enabled -> snapshot to enabled }.collect { (snapshot, enabled) ->
                _uiState.value = stateFor(snapshot)
                _meLabel.value = snapshot.displayName
                val key = snapshot.key.takeIf { enabled }
                if (key != syncKey) {
                    syncKey = key
                    syncJob?.cancel()
                    _message.value = null
                    if (key != null) syncJob = viewModelScope.launch {
                        try { synchronize() }
                        catch (cancelled: CancellationException) { throw cancelled }
                        catch (_: Exception) {
                            if (syncKey == key) _message.value = R.string.auth_sync_failed
                        }
                    }
                }
            }
        }
    }

    fun clearMessage() { _message.value = null }
    fun setSyncEnabled(enabled: Boolean) { syncAllowed.value = enabled }
    fun retryInitialization() {
        _uiState.value = if (runCatching { source.retryInitialization() }.getOrDefault(false))
            AuthUiState.Loading else AuthUiState.ConnectionFailed
    }
    fun signOut() {
        if (signingOut) return
        signingOut = true
        viewModelScope.launch {
            try {
                source.signOut()
                syncKey = null
                syncJob?.cancel()
                _meLabel.value = null
                _message.value = null
                _uiState.value = AuthUiState.SignedOut
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { _message.value = R.string.auth_sign_out_failed }
            finally { signingOut = false }
        }
    }
}

abstract class AuthFormViewModel : ViewModel() {
    private val _error = MutableStateFlow<Int?>(null)
    val error = _error.asStateFlow()
    private val _busy = MutableStateFlow(false)
    val busy = _busy.asStateFlow()
    protected fun perform(action: suspend () -> Unit) {
        if (_busy.value) return
        _busy.value = true
        _error.value = null
        viewModelScope.launch {
            try { action() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) {
                _error.value = (failure as? AuthActionException)?.messageResource ?: R.string.auth_action_failed
            } finally { _busy.value = false }
        }
    }
}

class SignInViewModel(private val client: AuthClient = ClerkAuthClient()) : AuthFormViewModel() {
    sealed interface State {
        data object Form : State
        data object NeedsClientTrust : State
    }
    private val _state = MutableStateFlow<State>(State.Form)
    val state: StateFlow<State> = _state.asStateFlow()
    private fun accept(step: AuthStep) {
        _state.value = if (step == AuthStep.COMPLETE) State.Form else State.NeedsClientTrust
    }
    fun signIn(email: String, password: String) = perform { accept(client.signIn(email, password)) }
    fun verifyClientTrust(code: String) = perform { accept(client.verifySignIn(code)) }
    fun resendClientTrustCode() = perform { client.resendSignIn() }
}

class SignUpViewModel(private val client: AuthClient = ClerkAuthClient()) : AuthFormViewModel() {
    sealed interface State {
        data object Form : State
        data object NeedsVerification : State
        data object Done : State
    }
    private val _state = MutableStateFlow<State>(State.Form)
    val state: StateFlow<State> = _state.asStateFlow()
    private fun accept(step: AuthStep) {
        _state.value = if (step == AuthStep.COMPLETE) State.Done else State.NeedsVerification
    }
    fun signUp(email: String, password: String) = perform { accept(client.signUp(email, password)) }
    fun verify(code: String) = perform { accept(client.verifySignUp(code)) }
    fun resendCode() = perform { client.resendSignUp() }
}
