package com.ivandsky.puzzlebox.app.e2e

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import com.ivandsky.puzzlebox.app.DrainTestApplication
import com.ivandsky.puzzlebox.data.model.PublishedPuzzle
import com.ivandsky.puzzlebox.data.repository.PublishedPuzzleRepository
import com.ivandsky.puzzlebox.games.queens.editor.QueensEditor
import com.ivandsky.puzzlebox.model.ShareCode
import com.ivandsky.puzzlebox.testing.repository.FakePublishedPuzzleRepository
import java.time.Instant
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Community › Yours on the tablet two-pane, over the real shell and the real create view model: a list scrolled by a
 * finger stays where the finger left it while the published rows re-emit and a row is picked for the right pane.
 */
@RunWith(RobolectricTestRunner::class)
// DrainTestApplication (plain Application + looper drain): the merged manifest's PuzzleBoxApplication would start its own Koin.
@Config(sdk = [34], application = DrainTestApplication::class, qualifiers = "w1280dp-h800dp")
class CommunityYoursScrollE2ETest {

    private val published = FakePublishedPuzzleRepository()
    private val stores = module { single<PublishedPuzzleRepository> { published } }

    @Test
    fun `a Yours list scrolled by a finger keeps its first visible row while its rows re-emit`() =
        shellRobot(graph = shellGraph(overrides = stores)) {
            runBlocking { repeat(ROWS) { published.upsert(row(it)) } }
            openYours()
            awaitTag(ROW)

            rule.onNodeWithTag(LIST).performTouchInput { swipeUp() }
            rule.waitForIdle()
            val first = firstVisible()
            assertTrue("the swipe scrolled the list", first > 0)

            runBlocking { published.upsert(row(ROWS - 1).copy(solvesCount = 3, solvesCountAt = NOW)) }
            rule.mainClock.advanceTimeBy(IDLE_MS)
            rule.waitForIdle()

            assertEquals("the list stayed where the finger left it", first, firstVisible())
        }

    @Test
    fun `a Yours list scrolled by a finger keeps its first visible row while a row is picked for the right pane`() =
        shellRobot(graph = shellGraph(overrides = stores)) {
            runBlocking { repeat(ROWS) { published.upsert(row(it)) } }
            openYours()
            awaitTag(ROW)

            rule.onNodeWithTag(LIST).performScrollToIndex(PICKED)
            rule.waitForIdle()
            val first = firstVisible()

            rule.onNodeWithText("Title $PICKED").performClick()
            rule.mainClock.advanceTimeBy(IDLE_MS)
            rule.waitForIdle()

            assertEquals("the list stayed where it was", first, firstVisible())
        }

    /** The index of the list's first visible item: what the semantics scroll range reports before its fraction. */
    private fun ShellRobot.firstVisible(): Int =
        rule.onNodeWithTag(LIST).fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value().toInt()

    private fun row(n: Int) = PublishedPuzzle(
        code = checkNotNull(ShareCode.parse("PK%04d".format(n))),
        gameId = QueensEditor.gameId,
        variant = QueensEditor.defaultVariant,
        title = "Title $n",
        authorTimeMs = 60_000L + n,
        authorHints = 0,
        createdAt = NOW.minusSeconds(3_600L * (n + 1)),
        unpublished = false,
        solvesCount = null,
        solvesCountAt = null,
    )

    private companion object {
        val NOW: Instant = Instant.parse("2026-11-18T10:00:00Z")
        const val ROWS = 15
        const val PICKED = 12
        const val IDLE_MS = 1_000L
        const val LIST = "custom.list"
        const val ROW = "create.published.row"
    }
}
