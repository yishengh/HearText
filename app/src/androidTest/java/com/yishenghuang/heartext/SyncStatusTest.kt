package com.yishenghuang.heartext

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import android.content.Context
import com.yishenghuang.heartext.ui.auth.SyncUiState
import com.yishenghuang.heartext.ui.profile.SyncStatusContent
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertEquals

class SyncStatusTest {
    @get:Rule val compose = createComposeRule()

    @Test fun incompleteSyncOffersRetryAndRunningOrOfflineDisablesIt() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        check(BuildConfig.APPLICATION_ID.endsWith(".validation"))
        val state = mutableStateOf(SyncUiState.INCOMPLETE)
        val enabled = mutableStateOf(true)
        var calls = 0
        compose.setContent { MaterialTheme { SyncStatusContent(state.value, enabled.value) { calls++ } } }
        compose.onNodeWithText(context.getString(R.string.sync_incomplete)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.action_retry)).performClick()
        assertEquals(1, calls)
        compose.runOnIdle { state.value = SyncUiState.RUNNING }
        compose.onNodeWithText(context.getString(R.string.sync_now)).assertIsNotEnabled()
        compose.runOnIdle { state.value = SyncUiState.COMPLETE }
        compose.onNodeWithText(context.getString(R.string.sync_complete)).assertIsDisplayed()
        compose.runOnIdle { enabled.value = false }
        compose.onNodeWithText(context.getString(R.string.sync_offline)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.sync_now)).assertIsNotEnabled()
    }
}
