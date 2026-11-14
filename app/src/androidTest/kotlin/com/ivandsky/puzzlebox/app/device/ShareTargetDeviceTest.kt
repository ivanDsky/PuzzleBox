package com.ivandsky.puzzlebox.app.device

import android.content.Context
import android.content.Intent
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ivandsky.puzzlebox.app.MainActivity
import com.ivandsky.puzzlebox.app.e2e.E2E_TODAY
import com.ivandsky.puzzlebox.app.e2e.E2eKoinGraph
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.Description
import org.junit.runner.RunWith
import org.junit.runners.model.Statement

/**
 * A real `ACTION_SEND` intent carrying a Zip notation text starts [MainActivity] and
 * the app lands on the Studio of an imported draft whose verdict pill
 * says Unique (spec A3, A28; runs in the device check, M5-19).
 */
@RunWith(AndroidJUnit4::class)
class ShareTargetDeviceTest {

    private val graph = E2eKoinGraph(E2E_TODAY)
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Before
    fun startGraph() = graph.start()

    @After
    fun stopGraph() = graph.stop()

    @Test
    fun sharingZipTextOpensTheStudioWithItsVerdict() {
        val rule = AndroidComposeTestRule(ActivityScenarioRule<MainActivity>(send(ZIP_UNIQUE)), ::activityOf)
        val run = object : Statement() {
            override fun evaluate() {
                rule.waitUntil(TIMEOUT_MS) {
                    val unique = SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Unique")
                    rule.onAllNodes(hasTestTag(VERDICT) and unique, useUnmergedTree = true)
                        .fetchSemanticsNodes().isNotEmpty()
                }
            }
        }
        rule.apply(run, Description.createTestDescription(ShareTargetDeviceTest::class.java, "share")).evaluate()
    }

    private fun send(text: String) = Intent(Intent.ACTION_SEND, null, context, MainActivity::class.java)
        .setType("text/plain")
        .putExtra(Intent.EXTRA_TEXT, text)

    private fun activityOf(scenario: ActivityScenarioRule<MainActivity>): MainActivity {
        var activity: MainActivity? = null
        scenario.scenario.onActivity { activity = it }
        return checkNotNull(activity) { "MainActivity is not running" }
    }

    private companion object {
        const val VERDICT = "create.editor.pill"
        const val TIMEOUT_MS = 20_000L

        // games/zip's zip-unique-6.txt fixture, embedded so :app needs no game test resources.
        const val ZIP_UNIQUE = "puzzlebox zip v1\n1 . . . . .\n. . . . . .\n. . . . . .\n" +
            "2 . . . . .\n. . . . . .\n4 . . . . 3\nwall 2,3 down\nwall 2,5 down\n" +
            "wall 4,3 down\nwall 4,5 down\n"
    }
}
