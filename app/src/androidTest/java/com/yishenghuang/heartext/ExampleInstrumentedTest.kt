package com.yishenghuang.heartext

import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4

import org.junit.Test
import org.junit.runner.RunWith

import org.junit.Assert.*

/**
 * Instrumented test, which will execute on an Android device.
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
@RunWith(AndroidJUnit4::class)
class ExampleInstrumentedTest {
    @Test
    fun useAppContext() {
        // Context of the app under test.
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals(BuildConfig.APPLICATION_ID, appContext.packageName)
    }

    @Test
    fun localValidationCannotUseProductionCredentialsOrEndpoint() {
        org.junit.Assume.assumeTrue(BuildConfig.APPLICATION_ID.endsWith(".validation"))
        assertTrue(BuildConfig.CLERK_PUBLISHABLE_KEY.isEmpty())
        assertEquals("http://10.0.2.2:18080", BuildConfig.API_BASE_URL)
    }
}
