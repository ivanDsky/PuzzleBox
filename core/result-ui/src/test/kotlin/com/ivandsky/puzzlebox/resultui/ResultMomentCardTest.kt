package com.ivandsky.puzzlebox.resultui

import android.provider.Settings
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.runtime.CompositionLocalProvider
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.domain.moments.Moment
import com.ivandsky.puzzlebox.gameui.GameBrand
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** L1: the moment card's rows, the late friend-board row, and the one buzz of a Result. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class ResultMomentCardTest {

    @get:Rule
    val composeRule = createComposeRule()

    private var moments by mutableStateOf<List<Moment>>(emptyList())
    private val buzzes = mutableListOf<HapticFeedbackType>()

    private val recorder = object : HapticFeedback {
        override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) {
            buzzes += hapticFeedbackType
        }
    }

    @After
    fun restoreMotion() = setAnimatorScale(1f)

    private fun setAnimatorScale(scale: Float) {
        val resolver = RuntimeEnvironment.getApplication().contentResolver
        Settings.Global.putFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, scale)
    }

    private fun show() {
        composeRule.setContent {
            PuzzleBoxTheme(darkTheme = false) {
                CompositionLocalProvider(LocalHapticFeedback provides recorder) {
                    ResultMomentCard(heroMoments(moments), GameBrand.Neutral)
                }
            }
        }
    }

    @Test
    fun `no moments draw no card and no buzz`() {
        show()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("result.moments").assertDoesNotExist()
        assertEquals(emptyList<HapticFeedbackType>(), buzzes)
    }

    @Test
    fun `one row per moment, strongest first, and one buzz for the strongest`() {
        moments = listOf(Moment.Streak(7), Moment.PersonalBest, Moment.FullSetDone())
        show()
        composeRule.mainClock.advanceTimeBy(1_500)
        composeRule.onNodeWithTag("result.moment.0").assertTextEquals("New personal best")
        composeRule.onNodeWithTag("result.moment.1").assertTextEquals("Full set done")
        composeRule.onNodeWithTag("result.moment.2").assertTextEquals("7-day streak")
        assertEquals(listOf(HapticFeedbackType.LongPress), buzzes)
    }

    @Test
    fun `the full-set row renders the day's formatted total`() {
        moments = listOf(Moment.FullSetDone(Moment.FullSetDone.DayTotal(timeMs = 312_000L, hints = 2)))
        show()
        composeRule.mainClock.advanceTimeBy(1_500)
        composeRule.onNodeWithTag("result.moment.0").assertTextEquals("Full set done · 5:12 total, 2 hints")
    }

    @Test
    fun `a late friend-board moment slides in and a Result keeps to one buzz`() {
        moments = listOf(Moment.FullSetDone())
        show()
        composeRule.mainClock.advanceTimeBy(1_500)
        assertEquals(listOf(HapticFeedbackType.TextHandleMove), buzzes)

        moments = listOf(Moment.FullSetDone(), Moment.TopThreeFriends(1))
        composeRule.mainClock.advanceTimeBy(1_500)
        composeRule.onNodeWithTag("result.moment.0").assertTextEquals("Top 3 among friends today")
        composeRule.onNodeWithTag("result.moment.1").assertTextEquals("Full set done")
        assertEquals("still the one buzz", 1, buzzes.size)
    }

    @Test
    fun `a late moment that is the first one buzzes when it arrives`() {
        show()
        composeRule.mainClock.advanceTimeBy(1_500)
        assertEquals(emptyList<HapticFeedbackType>(), buzzes)

        moments = listOf(Moment.TopThreeFriends(2))
        composeRule.mainClock.advanceTimeBy(100)
        composeRule.onNodeWithTag("result.moment.0").assertTextEquals("Top 3 among friends today")
        assertEquals(listOf(HapticFeedbackType.Confirm), buzzes)
    }

    @Test
    fun `reduced motion keeps the buzz and shows the late row at once`() {
        setAnimatorScale(0f)
        moments = listOf(Moment.PersonalBest)
        show()
        composeRule.mainClock.advanceTimeBy(16)
        assertEquals("the buzz waits for no beat", listOf(HapticFeedbackType.LongPress), buzzes)

        moments = listOf(Moment.PersonalBest, Moment.Streak(3))
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("result.moment.1").assertTextEquals("3-day streak")
        assertEquals(1, buzzes.size)
    }
}
