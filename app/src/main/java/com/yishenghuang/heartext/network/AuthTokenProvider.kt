package com.yishenghuang.heartext.network

import com.clerk.api.Clerk
import com.clerk.api.network.model.error.ClerkErrorResponse
import com.clerk.api.network.serialization.ClerkResult
import com.clerk.api.network.serialization.onFailure
import com.clerk.api.network.serialization.onSuccess
import com.clerk.api.session.GetTokenOptions
import kotlinx.coroutines.flow.first

/**
 * Provides Clerk session JWTs for Authorization: Bearer &lt;token&gt;.
 */
class AuthTokenProvider {
    val isSignedIn: Boolean
        get() = Clerk.userFlow.value != null

    suspend fun awaitReady() {
        Clerk.isInitialized.first { it }
    }

    suspend fun getToken(forceRefresh: Boolean = false): String {
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

internal fun ClerkResult.Failure<*>.message(): String {
    val err = error
    if (err is ClerkErrorResponse) {
        return err.errors.firstOrNull()?.longMessage
            ?: err.errors.firstOrNull()?.message
            ?: toString()
    }
    return throwable?.message ?: toString()
}
