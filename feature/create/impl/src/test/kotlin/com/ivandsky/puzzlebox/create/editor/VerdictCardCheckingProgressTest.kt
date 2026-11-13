package com.ivandsky.puzzlebox.create.editor

import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.game.editor.Solution
import com.ivandsky.puzzlebox.game.editor.Verdict
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A long check shows it is still working: past a short grace the card adds an indeterminate
 * progress cue under its title, gone the moment the verdict lands. Reduced motion: a static cue.
 * The verdict is held in Checking by the test (a fake analyzer that never answers), and time
 * moves only through the compose clock.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class VerdictCardCheckingProgressTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private var verdict: Verdict by mutableStateOf(Verdict.Checking)

    private fun show() {
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent { PuzzleBoxTheme { VerdictCard(verdict = verdict, onCheckAgain = {}) } }
    }

    private fun advance(ms: Long) = composeRule.mainClock.advanceTimeBy(ms)

    private fun cue() = composeRule.onNodeWithTag(PROGRESS)

    @Test
    fun `a check shorter than the grace shows no cue`() {
        show()
        advance(UNDER_GRACE_MS)

        cue().assertDoesNotExist()
    }

    @Test
    fun `a check past the grace shows an indeterminate cue`() {
        show()
        advance(PAST_GRACE_MS)

        cue().assertExists().assert(SemanticsMatcher.expectValue(SemanticsProperties.ProgressBarRangeInfo, ProgressBarRangeInfo.Indeterminate))
    }

    @Test
    fun `the cue goes the moment the verdict lands`() {
        show()
        advance(PAST_GRACE_MS)
        cue().assertExists()

        composeRule.runOnIdle { verdict = Verdict.Unique(Solution(listOf(0))) }
        composeRule.mainClock.advanceTimeByFrame()

        cue().assertDoesNotExist()
    }

    @Test
    fun `a verdict that lands within the grace never shows the cue`() {
        show()
        advance(UNDER_GRACE_MS)
        composeRule.runOnIdle { verdict = Verdict.Undetermined }
        advance(SETTLED_MS)

        cue().assertDoesNotExist()
    }

    @Test
    fun `a settled verdict shows no cue`() {
        verdict = Verdict.None(conflicts = setOf(0))
        show()
        advance(SETTLED_MS)

        cue().assertDoesNotExist()
    }

    @Test
    fun `under reduced motion the cue is static, not a running indicator`() {
        Settings.Global.putFloat(composeRule.activity.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
        show()
        advance(PAST_GRACE_MS)

        cue().assertExists().assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.ProgressBarRangeInfo))
    }

    private companion object {
        const val PROGRESS = "create.verdict.progress"
        const val UNDER_GRACE_MS = 300L
        const val PAST_GRACE_MS = 500L
        const val SETTLED_MS = 2_000L
    }
}
