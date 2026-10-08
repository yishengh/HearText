package com.yishenghuang.heartext.network

import com.clerk.api.Clerk
import com.clerk.api.network.model.error.ClerkErrorResponse
import com.clerk.api.network.serialization.ClerkResult
import com.clerk.api.network.serialization.onFailure
import com.clerk.api.network.serialization.onSuccess
import com.clerk.api.session.GetTokenOptions
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Provides Clerk session JWTs for Authorization: Bearer &lt;token&gt;.
 */
interface SessionTokenProvider {
    val isSignedIn: Boolean
    /** Changes when the user signs out, switches accounts, or starts another session. */
    val sessionKey: String?
    val accountId: String? get() = sessionKey
    suspend fun getToken(forceRefresh: Boolean = false): String
}

internal fun SessionTokenProvider.requireSession(expected: String?) {
    if (sessionKey != expected) throw kotlinx.coroutines.CancellationException("Session changed")
}

internal class ExpectedSession(val value: String?) : kotlin.coroutines.AbstractCoroutineContextElement(Key) {
    companion object Key : kotlin.coroutines.CoroutineContext.Key<ExpectedSession>
}

internal suspend fun SessionTokenProvider.requestSession(): String? {
    val expected = kotlinx.coroutines.currentCoroutineContext()[ExpectedSession]
    val session = if (expected != null) expected.value else sessionKey
    requireSession(session)
    return session
}

class AuthTokenProvider : SessionTokenProvider {
    override val accountId: String? get() = Clerk.userFlow.value?.id
    override val sessionKey: String?
        get() = Clerk.userFlow.value?.let { "${it.id}:${Clerk.session?.id}" }
    override val isSignedIn: Boolean
        get() = Clerk.userFlow.value != null

    suspend fun awaitReady() {
        awaitAuthReady(Clerk.isInitialized, Clerk.initializationError)
    }

    override suspend fun getToken(forceRefresh: Boolean): String {
        awaitReady()
        if (Clerk.userFlow.value == null) {
            error("Not signed in")
        }
        var jwt: String? = null
        var errorMsg: String? = null
        val options = GetTokenOptions(skipCache = forceRefresh)
        Clerk.auth.getToken(options)
            .onSuccess { token -> jwt = token }
            .onFailure { failure -> errorMsg = failure.message() }
        return jwt ?: error(errorMsg ?: "Empty session token")
    }
}

internal suspend fun awaitAuthReady(ready: Flow<Boolean>, failure: Flow<Throwable?>, timeoutMillis: Long = 15_000) {
    val initialized = withTimeoutOrNull(timeoutMillis) {
        combine(ready, failure) { initialized, error -> initialized to error }
            .first { (initialized, error) -> initialized || error != null }.first
    }
    check(initialized == true) { "Authentication unavailable" }
}

internal fun ClerkResult.Failure<*>.message(): String {
    val err = error
    if (err is ClerkErrorResponse) {
        return err.errors.firstOrNull()?.longMessage
            ?: err.errors.firstOrNull()?.message
            ?: toString()
    }
    return throwable?.message ?: toString()
}
