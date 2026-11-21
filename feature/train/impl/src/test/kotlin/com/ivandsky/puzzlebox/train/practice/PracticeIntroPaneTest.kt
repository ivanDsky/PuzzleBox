package com.ivandsky.puzzlebox.train.practice

import android.provider.Settings
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.ivandsky.puzzlebox.data.trainer.TechniqueProgress
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.gameui.trainer.IntroVariant
import com.ivandsky.puzzlebox.gameui.trainer.PracticeStatus
import com.ivandsky.puzzlebox.train.practice.PracticeRig.Companion.puzzle
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** The intro on the practice pane: the watching screen, its variant list and step track, the replay, and the chip with its hint. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PracticeIntroPaneTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val captions = listOf("Here it is.", "The evidence.", "The first square.", "The line is done.")
    private var started = 0
    private var closed = 0
    private var shown = 0
    private var hintDismissed = 0

    private fun intro(count: Int) = TechniqueIntro(
        List(count) { IntroEntry(IntroVariant("Variant $it", "Rule $it.", "Spot $it.", "Do $it."), StandInIntroDemo(it, captions)) },
        puzzle(1),
    )

    private fun controls(intro: TechniqueIntro?, replaying: Boolean = false, hint: Boolean = false) = IntroControls(
        intro = intro,
        replaying = replaying,
        hint = hint,
        demo = { _, _, modifier -> StandInGrid(modifier) },
        onShow = { shown += 1 },
        onClose = { closed += 1 },
        onStart = { started += 1 },
        onDismissHint = { hintDismissed += 1 },
    )

    private fun show(state: PracticeState, controls: IntroControls) {
        // Reduced motion: the landing step shows every square at once, so no frame is left to a running stagger.
        val resolver = RuntimeEnvironment.getApplication().contentResolver
        Settings.Global.putFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
        composeRule.setContent {
            PuzzleBoxTheme(darkTheme = false) {
                PracticePane(
                    chapter = standInChapter(board = 1),
                    state = state,
                    progress = TechniqueProgress(TechniqueProgress.Mark.Practicing, inRow = 2, answeredAtMs = 1L),
                    showBack = true,
                    onClose = {},
                    board = { _, modifier -> StandInGrid(modifier) },
                    onTryAgain = {},
                    onTryAnother = {},
                    hold = null,
                    onSkip = {},
                    onNextChapter = {},
                    onPracticeAgain = {},
                    intro = controls,
                )
            }
        }
    }

    /** Bug caught: the intro not first on the pane, or its title row still counting boards and showing the pips' count. */
    @Test
    fun `the opening intro is the watching screen with the title row's Watch first`() {
        val intro = intro(1)
        show(PracticeState.Intro(intro), controls(intro))

        composeRule.onNodeWithTag("train.watch").assertIsDisplayed()
        composeRule.onNodeWithText("Technique intro").assertIsDisplayed()
        composeRule.onNodeWithTag("train.watchFirst").assertTextEquals("Watch first")
        composeRule.onNodeWithText("Watching: Variant 0").assertIsDisplayed()
        composeRule.onNodeWithTag("train.watch.rule").assertExists()
    }

    /** Bug caught: a variant list for a technique with one variant, or none for a technique with two. */
    @Test
    fun `the variant list is only there with two or more variants`() {
        val one = intro(1)
        show(PracticeState.Intro(one), controls(one))
        composeRule.onNodeWithTag("train.watch.variants").assertDoesNotExist()
    }

    /** Bug caught: a variant list for a technique with one variant, or none for a technique with two. */
    @Test
    fun `two variants have a variant list that plays the one tapped`() {
        val two = intro(2)
        show(PracticeState.Intro(two), controls(two))

        composeRule.onNodeWithTag("train.watch.variants").assertExists()
        composeRule.onNodeWithTag("train.watch.variant.1").performScrollTo().performClick()

        composeRule.onNodeWithText("Watching: Variant 1").assertExists()
    }

    /** Bug caught: a step that does not show its frame, so the track is only decoration. */
    @Test
    fun `tapping a step shows that frame's caption`() {
        val intro = intro(1)
        show(PracticeState.Intro(intro), controls(intro))

        composeRule.onNodeWithTag("train.watch.step.1").performScrollTo().performClick()
        composeRule.onNodeWithTag("train.watch.caption", useUnmergedTree = true).assertTextEquals(captions[2])

        composeRule.onNodeWithTag("train.watch.step.0").performScrollTo().performClick()
        composeRule.onNodeWithTag("train.watch.caption", useUnmergedTree = true).assertTextEquals(captions[1])
    }

    /** Bug caught: an intro that opens on the plain position, a step (and a caption) per landing square, or a Next step with nothing after it. */
    @Test
    fun `the intro opens on the evidence and next step lands the squares as one step under the reason's caption`() {
        val intro = intro(1)
        show(PracticeState.Intro(intro), controls(intro))
        composeRule.onNodeWithTag("train.watch.caption", useUnmergedTree = true).assertTextEquals(captions[1])

        composeRule.onNodeWithTag("train.watch.next").performScrollTo().performClick()
        composeRule.onNodeWithTag("train.watch.caption", useUnmergedTree = true).assertTextEquals(captions[2])

        composeRule.onNodeWithTag("train.watch.step.1").assertExists()
        composeRule.onNodeWithTag("train.watch.step.2").assertDoesNotExist()
        composeRule.onNodeWithTag("train.watch.next").assertDoesNotExist()
    }

    /** Bug caught: Skip intro and Start practice not handing the board over, the one way out of the opening intro. */
    @Test
    fun `skip and start practice both hand over to board 1`() {
        val intro = intro(1)
        show(PracticeState.Intro(intro), controls(intro))

        composeRule.onNodeWithTag("train.watch.skip").performClick()
        composeRule.onNodeWithTag("train.watch.start").performScrollTo().performClick()

        assertEquals(2, started)
        assertEquals(0, closed)
    }

    /** Bug caught: the replay offering "Skip intro" (which has no board to hand over) or leaving through the hand-over instead of closing. */
    @Test
    fun `a replay over a board says Close and Back to board 1 and closes`() {
        val intro = intro(1)
        show(PracticeState.Practicing(StandInPracticeBoard(PracticeStatus.FindNextMove)), controls(intro, replaying = true))

        composeRule.onNodeWithText("Skip intro").assertDoesNotExist()
        composeRule.onNodeWithTag("train.watch.close").performClick()
        composeRule.onNodeWithTag("train.watch.start").performScrollTo().assertTextEquals("Back to board 1").performClick()

        assertEquals(2, closed)
        assertEquals(0, started)
    }

    /** Bug caught: the chip missing on a board of a technique with an intro, or shown where there is none, or on the board that is done. */
    @Test
    fun `the chip is on a live board of a technique with an intro and replays it`() {
        show(PracticeState.Practicing(StandInPracticeBoard(PracticeStatus.FindNextMove)), controls(intro(1)))

        composeRule.onNodeWithTag("train.howItWorks").performClick()

        assertEquals(1, shown)
    }

    @Test
    fun `no chip without an intro or on a done board`() {
        show(PracticeState.Practicing(StandInPracticeBoard(PracticeStatus.FindNextMove)), controls(null))
        composeRule.onNodeWithTag("train.howItWorks").assertDoesNotExist()
    }

    @Test
    fun `no chip on a done board`() {
        show(PracticeState.Practicing(StandInPracticeBoard(PracticeStatus.Done(null))), controls(intro(1)))
        composeRule.onNodeWithTag("train.howItWorks").assertDoesNotExist()
    }

    /** Bug caught: the hint bubble that cannot be answered, or one that shows when the hint is not due. */
    @Test
    fun `the hint is only there when due and its OK answers it`() {
        show(PracticeState.Practicing(StandInPracticeBoard(PracticeStatus.FindNextMove)), controls(intro(1), hint = true))

        composeRule.onNodeWithText("You can watch this intro again any time from here.").assertIsDisplayed()
        composeRule.onNodeWithTag("train.hint.ok").performClick()

        assertEquals(1, hintDismissed)
    }

    @Test
    fun `no hint when it is not due`() {
        show(PracticeState.Practicing(StandInPracticeBoard(PracticeStatus.FindNextMove)), controls(intro(1)))
        composeRule.onNodeWithTag("train.hint").assertDoesNotExist()
    }
}
