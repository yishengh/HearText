package com.yishenghuang.heartext

import android.os.SystemClock
import android.view.View
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.yishenghuang.heartext.ui.reader.engine.ReadView
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReaderRelayoutTest {
    @Test fun typographyAndWindowChangesKeepVisibleTextInTheSameChapter() {
        assumeTrue(BuildConfig.APPLICATION_ID.endsWith(".validation"))
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var reader: ReadView
            scenario.onActivity { activity ->
                reader = ReadView(activity)
                reader.setContentProvider { "A paragraph of familiar words to test stable reading positions.\n".repeat(900) }
                activity.setContentView(reader)
                reader.configure(32f, "day", 3, 1, 5)
            }
            awaitLoaded(scenario, reader)
            var anchor = 0
            scenario.onActivity {
                assertEquals(1, reader.getCurrentLocation()!!.first)
                anchor = reader.getCurrentPageStartCharacterOffset()!!
                assertTrue(anchor > 0)
                val location = reader.getCurrentLocation()!!
                // No font-size change: this used to retain the page number and skip text.
                reader.configure(32f, "day", 3, location.first, location.second,
                    lineHeightMult = 2.1f, letterSpacingDp = 1f, fontType = "serif")
            }
            awaitLoaded(scenario, reader)
            scenario.onActivity {
                assertTrue(anchor in reader.getCurrentPageCharacterRange()!!)
                anchor = reader.getCurrentPageStartCharacterOffset()!!
                // Exercise onLayout itself; its original pending chapter was unrelated
                // to subsequent navigation and previously sent the reader backwards.
                reader.jumpToCharacter(2, anchor)
            }
            awaitLoaded(scenario, reader)
            scenario.onActivity {
                anchor = reader.getCurrentPageStartCharacterOffset()!!
                val width = reader.width
                val height = reader.height / 2
                reader.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
                reader.layout(0, 0, width, height)
            }
            awaitLoaded(scenario, reader)
            scenario.onActivity {
                assertEquals(2, reader.getCurrentLocation()!!.first)
                assertTrue(anchor in reader.getCurrentPageCharacterRange()!!)
                anchor = reader.getCurrentPageStartCharacterOffset()!!
                reader.forceRelayout()
                assertNull(reader.getCurrentPageStartCharacterOffset())
            }
            awaitLoaded(scenario, reader)
            scenario.onActivity {
                assertTrue(anchor in reader.getCurrentPageCharacterRange()!!)
                reader.jumpToCharacter(2, Int.MAX_VALUE)
            }
            awaitLoaded(scenario, reader)
            scenario.onActivity {
                assertTrue(reader.getCurrentPageStartCharacterOffset()!! > anchor)
                // An explicit chapter jump must discard an unconsumed character anchor.
                reader.jumpToCharacter(1, 20_000)
                reader.jumpToChapter(0, 0)
            }
            awaitLoaded(scenario, reader)
            scenario.onActivity {
                assertEquals(0 to 0, reader.getCurrentLocation())
                assertEquals(0, reader.getCurrentPageStartCharacterOffset())
            }
        }
    }

    private fun awaitLoaded(scenario: ActivityScenario<MainActivity>, reader: ReadView) {
        val deadline = SystemClock.uptimeMillis() + 15_000
        while (SystemClock.uptimeMillis() < deadline) {
            var ready = false
            scenario.onActivity { ready = reader.getCurrentPageCharacterRange() != null }
            if (ready) return
            SystemClock.sleep(20)
        }
        fail("Reader did not finish pagination")
    }
}
