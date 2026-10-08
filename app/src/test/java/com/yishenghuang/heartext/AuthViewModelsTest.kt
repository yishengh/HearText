package com.yishenghuang.heartext

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStore
import com.yishenghuang.heartext.ui.auth.*
import com.yishenghuang.heartext.network.awaitAuthReady
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*
import java.io.IOException
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class AuthViewModelsTest {
    private val models = ViewModelStore()
    @Before fun setUp() { Dispatchers.setMain(StandardTestDispatcher()) }
    @After fun tearDown() {
        models.clear()
        runTest { runCurrent() }
        Dispatchers.resetMain()
    }
    private fun <T : ViewModel> keep(model: T): T = model.also { models.put(UUID.randomUUID().toString(), it) }
    private class Sessions : AuthSessionSource {
        val value = MutableStateFlow(AuthSnapshot(true, "a", "session-a", "Reader"))
        override val initial get() = value.value
        override val states get() = value
        var failSignOut = false
        override suspend fun signOut() {
            if (failSignOut) throw IOException("private error")
            value.value = AuthSnapshot(true, null, null)
        }
        override fun retryInitialization() = false
    }
    private class Client : AuthClient {
        var calls = 0
        var action: suspend () -> AuthStep = { AuthStep.COMPLETE }
        override suspend fun signIn(email: String, password: String): AuthStep { calls++; return action() }
        override suspend fun verifySignIn(code: String) = action()
        override suspend fun resendSignIn() { action() }
        override suspend fun signUp(email: String, password: String) = action()
        override suspend fun verifySignUp(code: String) = action()
        override suspend fun resendSignUp() { action() }
    }

    @Test fun existingSessionSyncsOnceAndChangingSessionCancelsOldWork() = runTest {
        val source = Sessions()
        val started = mutableListOf<String?>()
        val stopped = mutableListOf<String?>()
        val vm = keep(AuthViewModel({
            val id = source.value.value.sessionId
            started += id
            try { awaitCancellation() } finally { stopped += id }
        }, source, true))
        runCurrent()
        assertEquals(listOf("session-a"), started)
        source.value.value = source.value.value.copy(displayName = "Updated name")
        runCurrent()
        assertEquals(1, started.size)
        source.value.value = AuthSnapshot(true, "b", "session-b", "Other reader")
        runCurrent()
        assertEquals(listOf("session-a"), stopped)
        assertEquals(listOf("session-a", "session-b"), started)
        assertEquals("Other reader", vm.meLabel.value)
        vm.signOut(); runCurrent()
        assertEquals(listOf("session-a", "session-b"), stopped)
        assertEquals(AuthUiState.SignedOut, vm.uiState.value)
    }

    @Test fun failedSignOutKeepsSessionAndReportsLocalizedError() = runTest {
        val source = Sessions().apply { failSignOut = true }
        val vm = keep(AuthViewModel({}, source, true))
        runCurrent(); vm.signOut(); runCurrent()
        assertEquals(AuthUiState.SignedIn, vm.uiState.value)
        assertEquals(R.string.auth_sign_out_failed, vm.message.value)
        source.failSignOut = false
        vm.signOut(); runCurrent()
        assertEquals(AuthUiState.SignedOut, vm.uiState.value)
    }

    @Test fun initializationFailureOffersRecoverableStateInsteadOfInfiniteLoading() = runTest {
        val source = Sessions().apply { value.value = AuthSnapshot(false, null, null, initializationFailed = true) }
        val vm = keep(AuthViewModel({}, source, true))
        runCurrent()
        assertEquals(AuthUiState.ConnectionFailed, vm.uiState.value)
        vm.retryInitialization()
        assertEquals(AuthUiState.ConnectionFailed, vm.uiState.value)
    }

    @Test fun signInExceptionClearsBusyAndAllowsRetryWithoutLeakingDetails() = runTest {
        val client = Client().apply { action = { throw IOException("secret server detail") } }
        val vm = keep(SignInViewModel(client))
        vm.signIn("fixture@example.test", "test-password")
        runCurrent()
        assertFalse(vm.busy.value)
        assertEquals(R.string.auth_action_failed, vm.error.value)
        client.action = { AuthStep.EMAIL_VERIFICATION }
        vm.signIn("fixture@example.test", "test-password")
        runCurrent()
        assertEquals(SignInViewModel.State.NeedsClientTrust, vm.state.value)
        assertNull(vm.error.value)
    }

    @Test fun rapidSubmitDoesNotCreateDuplicateRequestsAndCancellationResetsBusy() = runTest {
        val client = Client().apply { action = { awaitCancellation() } }
        val vm = keep(SignInViewModel(client))
        repeat(2) { vm.signIn("fixture@example.test", "test-password") }
        runCurrent()
        assertEquals(1, client.calls)
        assertTrue(vm.busy.value)
        models.clear(); runCurrent()
        assertFalse(vm.busy.value)
        assertNull(vm.error.value)
    }

    @Test fun incompleteOrFailedActivationDoesNotCompleteSignUp() = runTest {
        val client = Client().apply { action = { AuthStep.EMAIL_VERIFICATION } }
        val vm = keep(SignUpViewModel(client))
        vm.signUp("fixture@example.test", "test-password"); runCurrent()
        client.action = { throw AuthActionException(R.string.auth_incomplete) }
        vm.verify("123456"); runCurrent()
        assertEquals(SignUpViewModel.State.NeedsVerification, vm.state.value)
        assertFalse(vm.busy.value)
        client.action = { AuthStep.COMPLETE }
        vm.verify("654321"); runCurrent()
        assertEquals(SignUpViewModel.State.Done, vm.state.value)
        assertNull(vm.error.value)
    }

    @Test fun offlineModeSuppressesStartupSyncAndCancelsRunningSync() = runTest {
        val source = Sessions()
        var started = 0
        var stopped = 0
        val vm = keep(AuthViewModel({
            started++
            try { awaitCancellation() } finally { stopped++ }
        }, source, true, syncEnabled = false))
        runCurrent()
        assertEquals(0, started)
        vm.setSyncEnabled(true); runCurrent()
        assertEquals(1, started)
        vm.setSyncEnabled(false); runCurrent()
        assertEquals(1, stopped)
        assertNull(vm.message.value)
    }

    @Test fun tokenInitializationFailureOrTimeoutDoesNotWaitForever() = runTest {
        val ready = MutableStateFlow(false)
        val failure = MutableStateFlow<Throwable?>(IOException("private detail"))
        assertTrue(runCatching { awaitAuthReady(ready, failure) }.exceptionOrNull() is IllegalStateException)
        failure.value = null
        assertTrue(runCatching { awaitAuthReady(ready, failure, 100) }.exceptionOrNull() is IllegalStateException)
        ready.value = true
        awaitAuthReady(ready, failure)
    }
}
