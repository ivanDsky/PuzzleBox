package com.ivandsky.puzzlebox.games.strandsui.chrome

import android.provider.Settings
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.ivandsky.puzzlebox.designsystem.CelebrationMotion
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.games.strands.Coord
import com.ivandsky.puzzlebox.games.strandsui.StrandsPlayController
import com.ivandsky.puzzlebox.games.strandsui.StrandsPlayScreen
import com.ivandsky.puzzlebox.games.strandsui.StrandsUiFixtures
import com.ivandsky.puzzlebox.gameui.Adopt
import com.ivandsky.puzzlebox.gameui.LocalSolveRestOnBoard
import com.ivandsky.puzzlebox.gameui.PlayBoardStatus
import com.ivandsky.puzzlebox.gameui.PlayEnvironment
import com.ivandsky.puzzlebox.gameui.PlayLayout
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Nav-rethink 104: with the rest on, a Strands solve stays on its board for the solve moment; a tap skips it. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-420dpi")
class StrandsSolveRestTest {

    @get:Rule
    val rule = createComposeRule()

    private var handOffs = 0
    private val buzzes = ArrayList<HapticFeedbackType>()

    /** Records the haptics asked of it: the solve buzz goes through [LocalHapticFeedback], where the Vibration switch gates it. */
    private val haptics = object : HapticFeedback {
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

    private fun adopted() = StrandsPlayController(
        PlayEnvironment(CoroutineScope(Dispatchers.Unconfined), {}, {}),
    ).also { assertEquals(Adopt.Adopted, it.adoptToday(StrandsUiFixtures.envelope(), StrandsUiFixtures.ref)) }

    private fun show(controller: StrandsPlayController, rest: Boolean) {
        rule.mainClock.autoAdvance = false
        rule.setContent {
            CompositionLocalProvider(LocalSolveRestOnBoard provides rest, LocalHapticFeedback provides haptics) {
                PuzzleBoxTheme(darkTheme = false) {
                    StrandsPlayScreen(
                        controller = controller,
                        status = PlayBoardStatus.Ready,
                        layout = PlayLayout.SinglePane,
                        sidePanel = null,
                        onRetry = {},
                        onSolved = { _, _ -> handOffs++ },
                    )
                }
            }
        }
        rule.mainClock.advanceTimeBy(16)
    }

    // Frame by frame: with nothing animating, one big step would skip past the timer's wake-up.
    private fun advance(ms: Long) = repeat((ms / STEP_MS).toInt()) { rule.mainClock.advanceTimeBy(STEP_MS) }

    private fun StrandsPlayController.solve() = (0 until 4).forEach { row ->
        StrandsUiFixtures.row(row).also { path ->
            path.forEach(::tap)
            tap(path.last())
        }
    }

    @Test
    fun `flag off, the hand-off is at once`() {
        val controller = adopted()
        show(controller, rest = false)
        rule.runOnIdle { controller.solve() }
        advance(500)
        assertEquals(1, handOffs)
    }

    @Test
    fun `with the rest on the solved board stays for the solve moment, then hands off once`() {
        val controller = adopted()
        show(controller, rest = true)
        rule.runOnIdle { controller.solve() }
        advance(CelebrationMotion.SolveMomentMs / 2)
        assertEquals("still resting", 0, handOffs)
        rule.onNodeWithTag("play.solve_rest").assertExists()

        advance(CelebrationMotion.SolveMomentMs)
        assertEquals(1, handOffs)
        rule.onNodeWithTag("play.solve_rest").assertDoesNotExist()
        advance(5_000)
        assertEquals("one solve, one hand-off", 1, handOffs)
    }

    @Test
    fun `with the rest on the success buzz comes once as the solve starts`() {
        val controller = adopted()
        show(controller, rest = true)
        rule.runOnIdle { controller.solve() }
        advance(200)
        assertEquals(listOf(HapticFeedbackType.Confirm), buzzes)
        advance(CelebrationMotion.SolveMomentMs * 2)
        assertEquals("once, not again at the hand-off", 1, buzzes.size)
    }

    @Test
    fun `flag off, a solve does not buzz`() {
        val controller = adopted()
        show(controller, rest = false)
        rule.runOnIdle { controller.solve() }
        advance(1_000)
        assertEquals(emptyList<HapticFeedbackType>(), buzzes)
    }

    @Test
    fun `a restored solved board does not buzz`() {
        val controller = adopted()
        controller.solve()
        show(controller, rest = true)
        advance(500)
        assertEquals(emptyList<HapticFeedbackType>(), buzzes)
    }

    @Test
    fun `a tap on the board during the rest hands off at once`() {
        val controller = adopted()
        show(controller, rest = true)
        rule.runOnIdle { controller.solve() }
        advance(600)
        rule.onNodeWithTag("play.solve_rest").performClick()
        rule.mainClock.advanceTimeBy(16)
        assertEquals(1, handOffs)
        advance(5_000)
        assertEquals(1, handOffs)
    }

    @Test
    fun `reduced motion keeps the same rest`() {
        setAnimatorScale(0f)
        val controller = adopted()
        show(controller, rest = true)
        rule.runOnIdle { controller.solve() }
        advance(CelebrationMotion.SolveMomentMs / 2)
        assertEquals(0, handOffs)
        advance(CelebrationMotion.SolveMomentMs)
        assertEquals(1, handOffs)
    }

    @Test
    fun `a restored solved board hands off at once even with the rest on`() {
        val controller = adopted()
        controller.solve()
        show(controller, rest = true)
        rule.mainClock.advanceTimeBy(16)
        assertEquals(1, handOffs)
        rule.onNodeWithTag("play.solve_rest").assertDoesNotExist()
    }

    private companion object {
        const val STEP_MS = 50L
    }
}
