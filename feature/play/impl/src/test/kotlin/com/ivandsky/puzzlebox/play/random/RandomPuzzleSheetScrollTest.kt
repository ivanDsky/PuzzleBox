package com.ivandsky.puzzlebox.play.random

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import com.ivandsky.puzzlebox.data.identity.Identity
import com.ivandsky.puzzlebox.data.repository.DailyDay
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.domain.ListCommunityPuzzles
import com.ivandsky.puzzlebox.domain.LoadPastDay
import com.ivandsky.puzzlebox.domain.random.LoadRandomDay
import com.ivandsky.puzzlebox.domain.random.LoadRandomPuzzle
import com.ivandsky.puzzlebox.domain.random.LoadRandomShared
import com.ivandsky.puzzlebox.gameui.GameBrand
import com.ivandsky.puzzlebox.gameui.PlayLayout
import com.ivandsky.puzzlebox.play.testDispatchers
import com.ivandsky.puzzlebox.testing.game.TestGames
import com.ivandsky.puzzlebox.testing.game.TestGames.alpha
import com.ivandsky.puzzlebox.testing.game.TestGames.beta
import com.ivandsky.puzzlebox.testing.repository.FakeCompletionRepository
import com.ivandsky.puzzlebox.testing.repository.FakeDailyRepository
import com.ivandsky.puzzlebox.testing.repository.FakeHiddenPuzzles
import com.ivandsky.puzzlebox.testing.repository.FakeIdentityService
import com.ivandsky.puzzlebox.testing.repository.FakeSharedPuzzleRepository
import java.time.LocalDate
import kotlin.math.abs
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

private const val FRAME_MS = 16L
private const val SETTLE_FRAMES = 60
private const val SHEET = "random.sheet"

/**
 * The random sheet where its body is taller than the sheet (a phone on its side), so the body scrolls inside it: a fling
 * that runs into either end of the body, and a re-emit of what it lists while nothing is touched, leave the sheet where it
 * is, and a drag reaches the sheet only from the body's top.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w640dp-h300dp-xhdpi")
class RandomPuzzleSheetScrollTest {
    @get:Rule
    val rule = createComposeRule()

    private val today = LocalDate.of(2026, 11, 18)
    private val daily = FakeDailyRepository().apply {
        listOf(alpha, beta).forEach { game ->
            setArchiveRange(game, today.minusDays(5)..today)
            (0L until 6).forEach { setDay(game, today.minusDays(it), DailyDay.Ready(TestGames.puzzle(game), 100)) }
        }
    }
    private fun offered() = listOf(alpha, beta).map { RandomGame(it, it.value.replaceFirstChar(Char::uppercase), GameBrand.Neutral) }
    private var games by mutableStateOf(offered())

    private fun show() {
        val registry = TestGames.registry(listOf(alpha, beta))
        val random = Random(1)
        val load = LoadRandomPuzzle(
            LoadRandomDay(daily, FakeCompletionRepository(), LoadPastDay(daily, registry), registry, random),
            LoadRandomShared(ListCommunityPuzzles(FakeSharedPuzzleRepository(), FakeHiddenPuzzles(), FakeIdentityService(Identity("me", "Me", null))) { 0L }, random),
        )
        val viewModel = RandomPuzzleViewModel(alpha, load, RandomPickMemory(), testDispatchers())
        rule.setContent {
            PuzzleBoxTheme {
                RandomPuzzleSheet(alpha, games, games, PlayLayout.SinglePane, onOpen = {}, onDismiss = {}, viewModel = viewModel)
            }
        }
        rule.waitForIdle()
        rule.mainClock.autoAdvance = false
        rule.mainClock.advanceTimeBy(1000)
    }

    /** The sheet's position is its drag handle's: the sheet's own tag sits outside the offset the drag applies. */
    private fun sheetTop() = rule.onNode(hasContentDescription("Drag handle")).fetchSemanticsNode().boundsInRoot.top

    private fun bodyScroll() = rule.onNode(hasScrollAction()).fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value()

    private fun worstDrift(rest: Float): Float {
        var worst = 0f
        repeat(SETTLE_FRAMES) {
            rule.mainClock.advanceTimeBy(FRAME_MS)
            worst = maxOf(worst, abs(sheetTop() - rest))
        }
        return worst
    }

    /**
     * A flick shorter than the body's scroll range, so the finger never reaches the sheet and only the fling runs on to the
     * body's end. The sheet is as tall as the window here, so its tag's centre is a point on the body.
     */
    private fun flingBody(dy: Float) = rule.onNodeWithTag(SHEET).performTouchInput {
        swipe(center, Offset(centerX, centerY + dy), 40)
    }

    @Test
    fun `a body fling to its end and back to its top leaves the sheet where it is`() {
        show()
        val rest = sheetTop()

        flingBody(-80f)
        assertEquals("sheet moved on its own after the body was let go", 0f, worstDrift(rest), 1f)
        assertTrue("body did not scroll", bodyScroll() > 0f)
        flingBody(80f)
        assertEquals("sheet moved on its own after the body was let go", 0f, worstDrift(rest), 1f)
        assertEquals("body did not scroll back", 0f, bodyScroll(), 0f)
    }

    @Test
    fun `a re-emit of the games while nothing is touched moves neither the sheet nor the body`() {
        show()
        flingBody(-80f)
        rule.mainClock.advanceTimeBy(1000)
        val rest = sheetTop()
        val scrolled = bodyScroll()

        rule.runOnUiThread { games = offered() }
        assertEquals("sheet moved on a re-emit", 0f, worstDrift(rest), 1f)
        assertEquals("body moved on a re-emit", scrolled, bodyScroll(), 0f)
    }

    @Test
    fun `a slow drag down scrolls the body to its top before the sheet moves`() {
        show()
        val rest = sheetTop()
        flingBody(-80f)
        rule.mainClock.advanceTimeBy(1000)
        val scrolled = bodyScroll()

        rule.onNodeWithTag(SHEET).performTouchInput {
            down(center)
            repeat(2) { moveBy(Offset(0f, 30f), FRAME_MS) }
        }
        rule.mainClock.advanceTimeBy(FRAME_MS)
        assertEquals("sheet moved while the body could still scroll", rest, sheetTop(), 1f)
        assertTrue("body did not scroll back", bodyScroll() < scrolled)

        rule.onNodeWithTag(SHEET).performTouchInput {
            repeat(20) { moveBy(Offset(0f, 30f), FRAME_MS) }
        }
        rule.mainClock.advanceTimeBy(FRAME_MS)
        assertEquals(0f, bodyScroll(), 0f)
        assertTrue("sheet did not follow once the body was at its top", sheetTop() > rest + 50f)
        rule.onNodeWithTag(SHEET).performTouchInput {
            advanceEventTime(300)
            up()
        }
    }
}
