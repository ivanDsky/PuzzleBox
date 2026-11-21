package com.ivandsky.puzzlebox.app.e2e

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Looper
import android.view.WindowManager
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.junit4.v2.AndroidComposeTestRule as v2AndroidComposeTestRule
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.rules.ActivityScenarioRule
import com.ivandsky.puzzlebox.app.DrainTestApplication
import com.ivandsky.puzzlebox.app.MainActivity
import com.ivandsky.puzzlebox.data.repository.DraftRepository
import com.ivandsky.puzzlebox.testing.repository.FakeDraftRepository
import java.time.Duration
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.Description
import org.junit.runner.RunWith
import org.junit.runners.model.Statement
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Device check R3: the keyboard adjusts the app's window by resize, never by pan (a pan slid the
 * Studio's top bar under the status bar, and below API 30 left no IME inset to scroll by). The
 * manifest declares it on [MainActivity], so the system never re-picks pan, and the import
 * sheet's text field on Community's Yours page, the main window's other text field, still takes input under it.
 */
@RunWith(RobolectricTestRunner::class)
// DrainTestApplication (plain Application + looper drain): the merged manifest's PuzzleBoxApplication would start its own Koin.
@Config(sdk = [34], application = DrainTestApplication::class, qualifiers = "w411dp-h891dp")
class KeyboardModeE2ETest {

    private lateinit var rule: AndroidComposeTestRule<ActivityScenarioRule<MainActivity>, MainActivity>

    private val drafts = FakeDraftRepository()
    private val graph = E2eKoinGraph(E2E_TODAY, overrides = module { single<DraftRepository> { drafts } })
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @After
    fun stopGraph() = graph.stop()

    @Test
    fun `the activity declares that the keyboard resizes its window`() {
        val info = context.packageManager.getActivityInfo(ComponentName(context, MainActivity::class.java), 0)
        assertEquals(RESIZE, info.softInputMode and ADJUST)
    }

    @Test
    fun `the Custom import sheet's text field takes input in the resizing window`() {
        graph.start()
        launching {
            assertEquals(RESIZE, rule.activity.window.attributes.softInputMode and ADJUST)
            tap("nav.tab.community")
            tap(YOURS)
            // Under Community's large top bar the empty state's Import sits below the first screen: scroll to it, as a player does.
            awaitIdling { count(IMPORT) > 0 }
            rule.onNodeWithTag(IMPORT).performScrollTo()
            tap(IMPORT)
            tap(TEXT)
            rule.onNodeWithTag(TEXT).performTextInput("puzzlebox queens v1")
            rule.onNodeWithTag(TEXT).assertTextContains("puzzlebox queens v1", substring = true)
        }
    }

    /** Runs [block] over [MainActivity] launched from the home screen, torn down by the compose rule. */
    private fun launching(block: () -> Unit) {
        val intent = Intent(Intent.ACTION_MAIN, null, context, MainActivity::class.java).addCategory(Intent.CATEGORY_LAUNCHER)
        rule = v2AndroidComposeTestRule(ActivityScenarioRule(intent), activityProvider = ::activityOf)
        val run = object : Statement() {
            override fun evaluate() = block()
        }
        rule.apply(run, Description.createTestDescription(KeyboardModeE2ETest::class.java, "launching")).evaluate()
    }

    private fun activityOf(scenario: ActivityScenarioRule<MainActivity>): MainActivity {
        var activity: MainActivity? = null
        scenario.scenario.onActivity { activity = it }
        return checkNotNull(activity) { "MainActivity is not running" }
    }

    private fun tap(tag: String) {
        awaitIdling { count(tag) > 0 }
        rule.onNodeWithTag(tag).performClick()
        rule.waitForIdle()
    }

    private fun count(tag: String) = rule.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().size

    /** A coroutine hop to the main looper never runs under the compose rule's frame-only wait. */
    private fun awaitIdling(condition: () -> Boolean) {
        rule.waitUntil(TIMEOUT_MS) {
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(IDLE_STEP_MS))
            condition()
        }
    }

    private companion object {
        const val ADJUST = WindowManager.LayoutParams.SOFT_INPUT_MASK_ADJUST

        // The manifest's android:windowSoftInputMode="adjustResize" is what this test pins, and the
        // platform has no non-deprecated constant for reading that mode back from the window.
        @Suppress("DEPRECATION")
        const val RESIZE = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        const val YOURS = "community.tab.yours"
        const val IMPORT = "create.empty.import"
        const val TEXT = "create.import.text"
        const val TIMEOUT_MS = 10_000L
        const val IDLE_STEP_MS = 50L
    }
}
