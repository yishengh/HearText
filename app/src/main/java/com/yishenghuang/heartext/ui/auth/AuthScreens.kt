package com.yishenghuang.heartext.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yishenghuang.heartext.R
import com.yishenghuang.heartext.ui.theme.HearPurple
import com.yishenghuang.heartext.util.LegalLinks

@Composable
fun AuthGate(
    authViewModel: AuthViewModel,
    onContinueOffline: () -> Unit,
    signedInContent: @Composable () -> Unit
) {
    val state by authViewModel.uiState.collectAsStateWithLifecycle()
    // Keep main UI mounted across Clerk Loading after a prior sign-in (e.g. rotation).
    var hadSignedIn by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(state) {
        if (state is AuthUiState.SignedIn) hadSignedIn = true
        if (state is AuthUiState.SignedOut) hadSignedIn = false
    }

    when {
        state is AuthUiState.SignedIn || (hadSignedIn && state is AuthUiState.Loading) -> {
            signedInContent()
        }
        state is AuthUiState.Loading -> {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(HearPurple),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                CircularProgressIndicator(color = Color.White)
                Spacer(Modifier.height(12.dp))
                Text(stringResource(R.string.auth_connecting), color = Color.White)
                TextButton(onClick = onContinueOffline) {
                    Text(stringResource(R.string.auth_continue_offline), color = Color.White)
                }
            }
        }
        state is AuthUiState.ConnectionFailed -> {
            Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center) {
                Text(stringResource(R.string.auth_connection_failed))
                Button(onClick = authViewModel::retryInitialization) { Text(stringResource(R.string.action_retry)) }
                TextButton(onClick = onContinueOffline) { Text(stringResource(R.string.auth_continue_offline)) }
            }
        }
        state is AuthUiState.MissingClerkKey -> {
            MissingKeyScreen(onContinueOffline)
        }
        else -> {
            SignInOrUpScreen(onContinueOffline = onContinueOffline)
        }
    }
}

@Composable
private fun MissingKeyScreen(onContinueOffline: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(HearPurple)
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            stringResource(R.string.auth_clerk_missing),
            style = MaterialTheme.typography.headlineMedium,
            color = Color.White
        )
        Spacer(Modifier.height(20.dp))
        Button(
            onClick = onContinueOffline,
            colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = HearPurple)
        ) {
            Text(stringResource(R.string.auth_continue_offline))
        }
    }
}

@Composable
fun SignInOrUpScreen(onContinueOffline: () -> Unit) {
    val context = LocalContext.current
    var isSignUp by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(HearPurple)
            .imePadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.displayLarge, color = Color.White)
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.auth_blurb),
            color = Color.White.copy(alpha = 0.9f)
        )
        Spacer(Modifier.height(24.dp))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White, RoundedCornerShape(24.dp))
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (isSignUp) {
                SignUpForm()
            } else {
                SignInForm()
            }
            TextButton(onClick = { isSignUp = !isSignUp }, modifier = Modifier.fillMaxWidth()) {
                Text(
                    if (isSignUp) stringResource(R.string.auth_have_account)
                    else stringResource(R.string.auth_need_account)
                )
            }
            TextButton(onClick = onContinueOffline, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.auth_continue_offline))
            }
            TextButton(
                onClick = { LegalLinks.openPrivacyPolicy(context) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.privacy_policy))
            }
        }
    }
}

@Composable
private fun SignInForm(viewModel: SignInViewModel = viewModel()) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()

    Text(stringResource(R.string.sign_in), style = MaterialTheme.typography.titleLarge)
    when (state) {
        SignInViewModel.State.NeedsClientTrust -> {
            OutlinedTextField(
                value = code,
                onValueChange = { code = it },
                label = { Text(stringResource(R.string.auth_code)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )
            Button(
                onClick = { viewModel.verifyClientTrust(code) },
                enabled = !busy && code.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = HearPurple)
            ) {
                if (busy) {
                    CircularProgressIndicator(
                        color = Color.White,
                        strokeWidth = 2.dp,
                        modifier = Modifier.height(20.dp)
                    )
                } else {
                    Text(stringResource(R.string.auth_verify))
                }
            }
            TextButton(
                onClick = { viewModel.resendClientTrustCode() },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.auth_resend))
            }
        }
        SignInViewModel.State.Form -> {
            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text(stringResource(R.string.auth_email)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
            )
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text(stringResource(R.string.auth_password)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                visualTransformation = PasswordVisualTransformation()
            )
            Button(
                onClick = { viewModel.signIn(email, password) },
                enabled = !busy && email.isNotBlank() && password.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = HearPurple)
            ) {
                if (busy) {
                    CircularProgressIndicator(
                        color = Color.White,
                        strokeWidth = 2.dp,
                        modifier = Modifier.height(20.dp)
                    )
                } else {
                    Text(stringResource(R.string.sign_in))
                }
            }
        }
    }
    error?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.error) }
}

@Composable
private fun SignUpForm(viewModel: SignUpViewModel = viewModel()) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()

    Text(stringResource(R.string.auth_create_account), style = MaterialTheme.typography.titleLarge)
    when (state) {
        SignUpViewModel.State.NeedsVerification -> {
            OutlinedTextField(
                value = code,
                onValueChange = { code = it },
                label = { Text(stringResource(R.string.auth_code)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Button(
                onClick = { viewModel.verify(code) },
                enabled = !busy && code.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = HearPurple)
            ) { Text(stringResource(R.string.auth_verify)) }
            TextButton(onClick = viewModel::resendCode, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.auth_resend))
            }
        }
        else -> {
            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text(stringResource(R.string.auth_email)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
            )
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text(stringResource(R.string.auth_password)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                visualTransformation = PasswordVisualTransformation()
            )
            Button(
                onClick = { viewModel.signUp(email, password) },
                enabled = !busy && email.isNotBlank() && password.length >= 8,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = HearPurple)
            ) { Text(stringResource(R.string.auth_sign_up)) }
        }
    }
    error?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.error) }
}
