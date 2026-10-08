package com.yishenghuang.heartext

import android.os.SystemClock
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.core.app.ActivityScenario
import com.yishenghuang.heartext.ui.reader.engine.ReadView
import com.yishenghuang.heartext.ui.reader.engine.ReadViewCallbacks
import org.junit.Test
import org.junit.Assert.*

class ReaderAccessibilityTest {
    @Test fun currentTextExposesMenuAndBoundedPageActionsWithoutPreloadedPages() {
        check(BuildConfig.APPLICATION_ID.endsWith(".validation"))
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var reader: ReadView
            var menus = 0
            scenario.onActivity { activity ->
                reader = ReadView(activity)
                reader.setContentProvider { chapter -> "Chapter $chapter accessible reader fixture. ".repeat(900) }
                reader.setCallbacks(object : ReadViewCallbacks {
                    override fun onPageChanged(globalPage: Int, chapterIndex: Int, pageInChapter: Int, chapterTotalPages: Int) {}
                    override fun onMenuToggle() { menus++ }
                    override fun onLoadingChanged(isLoading: Boolean) {}
                })
                activity.setContentView(reader)
                reader.configure(32f, "day", 2, 0, 0)
            }
            await(scenario) { reader.slotManager.getCurSlot().isLoaded && reader.slotManager.getNextSlot().isLoaded }
            scenario.onActivity {
                val current = reader.slotManager.getCurSlot().contentView
                for (page in listOf(reader.prevPageView, reader.curPageView, reader.nextPageView)) {
                    assertEquals(if (page === current) View.IMPORTANT_FOR_ACCESSIBILITY_AUTO
                        else View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS, page.importantForAccessibility)
                }
                val node = current.textView.createAccessibilityNodeInfo()
                assertEquals(current.textView.text.toString(), node.text.toString())
                assertTrue(node.text.isNotEmpty())
                assertTrue(node.actionList.any { it.id == AccessibilityNodeInfo.ACTION_SCROLL_FORWARD })
                assertFalse(node.actionList.any { it.id == AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD })
                assertTrue(current.textView.performAccessibilityAction(AccessibilityNodeInfo.ACTION_CLICK, null))
                assertEquals(1, menus)
                assertTrue(current.textView.performAccessibilityAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD, null))
            }
            await(scenario) { reader.getCurrentLocation() == (0 to 1) && !reader.animationController.isRunning }
            scenario.onActivity {
                val text = reader.slotManager.getCurSlot().contentView.textView
                assertTrue(text.createAccessibilityNodeInfo().actionList.any { it.id == AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD })
                assertTrue(text.performAccessibilityAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD, null))
            }
            await(scenario) { reader.getCurrentLocation() == (0 to 0) && !reader.animationController.isRunning }
            scenario.onActivity { reader.jumpToCharacter(1, Int.MAX_VALUE) }
            await(scenario) { reader.getCurrentLocation()?.first == 1 && reader.getCurrentPageCharacterRange() != null }
            scenario.onActivity {
                val text = reader.slotManager.getCurSlot().contentView.textView
                assertFalse(text.createAccessibilityNodeInfo().actionList.any { it.id == AccessibilityNodeInfo.ACTION_SCROLL_FORWARD })
                assertFalse(text.performAccessibilityAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD, null))
            }
        }
    }

    private fun await(scenario: ActivityScenario<MainActivity>, condition: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 15_000
        while (SystemClock.uptimeMillis() < deadline) {
            var ready = false
            scenario.onActivity { ready = condition() }
            if (ready) return
            SystemClock.sleep(20)
        }
        fail("Reader accessibility state did not become ready")
    }
}
