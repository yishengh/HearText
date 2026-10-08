package com.yishenghuang.heartext.ui.auth

import com.clerk.api.Clerk
import com.clerk.api.network.serialization.onFailure
import com.yishenghuang.heartext.R
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

data class AuthSnapshot(val ready: Boolean, val userId: String?, val sessionId: String?,
    val displayName: String? = null, val initializationFailed: Boolean = false) {
    val key: String? get() = if (ready && userId != null && sessionId != null) "$userId:$sessionId" else null
}

interface AuthSessionSource {
    val initial: AuthSnapshot
    val states: Flow<AuthSnapshot>
    suspend fun signOut()
    fun retryInitialization(): Boolean
}

object ClerkSessionSource : AuthSessionSource {
    override val initial get() = AuthSnapshot(Clerk.isInitialized.value, Clerk.userFlow.value?.id,
        Clerk.sessionFlow.value?.id, initializationFailed = Clerk.initializationError.value != null)
    override val states: Flow<AuthSnapshot> get() = combine(
        Clerk.isInitialized, Clerk.userFlow, Clerk.sessionFlow, Clerk.initializationError
    ) { ready, user, session, error ->
        val name = user?.username?.takeIf { it.isNotBlank() }
            ?: listOfNotNull(user?.firstName, user?.lastName).joinToString(" ").takeIf { it.isNotBlank() }
        AuthSnapshot(ready, user?.id, session?.id, name, error != null)
    }
    override suspend fun signOut() {
        Clerk.auth.signOut().onFailure { throw AuthActionException(R.string.auth_sign_out_failed) }
    }
    override fun retryInitialization() = Clerk.reinitialize()
}
