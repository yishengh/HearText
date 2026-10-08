package com.yishenghuang.heartext.ui.auth

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
import com.yishenghuang.heartext.R

enum class AuthStep { COMPLETE, EMAIL_VERIFICATION }
class AuthActionException(val messageResource: Int) : Exception()

interface AuthClient {
    suspend fun signIn(email: String, password: String): AuthStep
    suspend fun verifySignIn(code: String): AuthStep
    suspend fun resendSignIn()
    suspend fun signUp(email: String, password: String): AuthStep
    suspend fun verifySignUp(code: String): AuthStep
    suspend fun resendSignUp()
}

/** SDK boundary: only an activated session is considered complete. */
class ClerkAuthClient : AuthClient {
    override suspend fun signIn(email: String, password: String): AuthStep {
        var result: SignIn? = null
        Clerk.auth.signInWithPassword { identifier = email.trim(); this.password = password }
            .onSuccess { result = it }.onFailure { fail() }
        return finishSignIn(result ?: fail())
    }

    override suspend fun verifySignIn(code: String): AuthStep {
        val attempt = Clerk.auth.currentSignIn ?: expired()
        var result: SignIn? = null
        attempt.verifyMfaCode(code.trim(), MfaType.EMAIL_CODE)
            .onSuccess { result = it }.onFailure { fail() }
        return finishSignIn(result ?: fail())
    }

    override suspend fun resendSignIn() {
        val attempt = Clerk.auth.currentSignIn ?: expired()
        attempt.sendMfaEmailCode().onFailure { fail() }
    }

    private suspend fun finishSignIn(attempt: SignIn): AuthStep = when (attempt.status) {
        SignIn.Status.COMPLETE -> { activate(attempt.createdSessionId); AuthStep.COMPLETE }
        SignIn.Status.NEEDS_CLIENT_TRUST, SignIn.Status.NEEDS_SECOND_FACTOR -> {
            val factor = attempt.supportedSecondFactors.orEmpty().firstOrNull { it.strategy == "email_code" }
                ?: throw AuthActionException(R.string.auth_unsupported_verification)
            attempt.sendMfaEmailCode(emailAddressId = factor.emailAddressId).onFailure { fail() }
            AuthStep.EMAIL_VERIFICATION
        }
        else -> throw AuthActionException(R.string.auth_incomplete)
    }

    override suspend fun signUp(email: String, password: String): AuthStep {
        var result: SignUp? = null
        Clerk.auth.signUp { this.email = email.trim(); this.password = password }
            .onSuccess { result = it }.onFailure { fail() }
        val attempt = result ?: fail()
        if (attempt.status == SignUp.Status.COMPLETE) {
            activate(attempt.createdSessionId)
            return AuthStep.COMPLETE
        }
        if (attempt.status != SignUp.Status.MISSING_REQUIREMENTS) throw AuthActionException(R.string.auth_incomplete)
        attempt.sendCode { this.email = email.trim() }.onFailure { fail() }
        return AuthStep.EMAIL_VERIFICATION
    }

    override suspend fun verifySignUp(code: String): AuthStep {
        val attempt = Clerk.auth.currentSignUp ?: expired()
        var result: SignUp? = null
        attempt.verifyCode(code.trim(), VerificationType.EMAIL)
            .onSuccess { result = it }.onFailure { fail() }
        val updated = result ?: fail()
        if (updated.status != SignUp.Status.COMPLETE) throw AuthActionException(R.string.auth_incomplete)
        activate(updated.createdSessionId)
        return AuthStep.COMPLETE
    }

    override suspend fun resendSignUp() {
        val attempt = Clerk.auth.currentSignUp ?: expired()
        val email = attempt.emailAddress ?: expired()
        attempt.sendCode { this.email = email }.onFailure { fail() }
    }

    private suspend fun activate(sessionId: String?) {
        if (sessionId == null) throw AuthActionException(R.string.auth_incomplete)
        if (Clerk.session?.id == sessionId) return
        Clerk.auth.setActive(sessionId).onFailure { fail() }
    }

    private fun fail(): Nothing = throw AuthActionException(R.string.auth_action_failed)
    private fun expired(): Nothing = throw AuthActionException(R.string.auth_attempt_expired)
}
