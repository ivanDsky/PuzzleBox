package com.ivandsky.puzzlebox.app.e2e

import androidx.compose.ui.test.ComposeTimeoutException
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import com.ivandsky.puzzlebox.app.DrainTestApplication
import com.ivandsky.puzzlebox.app.play.MiniSudokuPlayFixture
import com.ivandsky.puzzlebox.app.play.QueensPlayFixture
import com.ivandsky.puzzlebox.app.play.TangoPlayFixture
import com.ivandsky.puzzlebox.app.play.ZipPlayFixture
import com.ivandsky.puzzlebox.data.linkedin.LinkedInLink
import com.ivandsky.puzzlebox.data.repository.DailyEntry
import com.ivandsky.puzzlebox.game.CompletionRecord
import com.ivandsky.puzzlebox.game.DailySource
import com.ivandsky.puzzlebox.game.GameRegistry
import com.ivandsky.puzzlebox.game.Puzzle
import com.ivandsky.puzzlebox.game.linkedin.LinkedInAccountSource
import com.ivandsky.puzzlebox.game.linkedin.LinkedInOwnResult
import com.ivandsky.puzzlebox.game.linkedin.LinkedInPhotoSource
import com.ivandsky.puzzlebox.game.linkedin.LinkedInRead
import com.ivandsky.puzzlebox.game.linkedin.LinkedInResultPoster
import com.ivandsky.puzzlebox.game.linkedin.LinkedInSessionCookies
import com.ivandsky.puzzlebox.games.tango.TangoSession
import com.ivandsky.puzzlebox.model.PuzzleKey
import com.ivandsky.puzzlebox.model.SolveReplay
import com.ivandsky.puzzlebox.play.api.PlayKey
import com.ivandsky.puzzlebox.play.api.ResultKey
import com.ivandsky.puzzlebox.testing.linkedin.FakeLinkedInAccountSource
import com.ivandsky.puzzlebox.testing.linkedin.FakeLinkedInPhotoSource
import com.ivandsky.puzzlebox.testing.linkedin.FakeLinkedInResultPoster
import com.ivandsky.puzzlebox.testing.linkedin.FakeLinkedInSessionCookies
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The result page's Post to LinkedIn row, through the real shell, for a player with a linked account and posting
 * on. Every game that posts has two rows here, so a game whose own solve cannot be offered fails by name: the solve
 * made just now on the game's own board, and the solve an earlier launch stored, opened cold from its record.
 *
 * The graph's daily repository is a fake, so each test hands it the LinkedIn facts of the game's registered
 * [DailySource], the ones the real repository reads: a fact that source does not report hides the row here as it
 * does on a device. Every LinkedIn edge answers as with no network, except where a test hands one an answer: the row
 * needs no call to show, but a Post the phone would offer waits for LinkedIn's word on the day.
 */
@RunWith(RobolectricTestRunner::class)
// DrainTestApplication (plain Application + looper drain): the merged manifest's PuzzleBoxApplication would start its own Koin.
@Config(sdk = [34], application = DrainTestApplication::class, qualifiers = "w411dp-h891dp")
class LinkedInPostRowE2ETest {

    @Test fun `a Tango solve offers Post to LinkedIn`() = aSolveOffersPost(DailySolve.Tango, TangoPlayFixture.envelope())

    @Test fun `a Queens solve offers Post to LinkedIn`() = aSolveOffersPost(DailySolve.Queens, QueensPlayFixture.envelope())

    @Test fun `a Zip solve offers Post to LinkedIn`() = aSolveOffersPost(DailySolve.Zip, ZipPlayFixture.envelope())

    @Test
    fun `a Mini Sudoku solve offers Post to LinkedIn`() =
        aSolveOffersPost(DailySolve.MiniSudoku, MiniSudokuPlayFixture.envelope())

    @Test
    fun `a stored Tango solve opened cold offers Post to LinkedIn`() =
        aStoredSolveOffersPost(DailySolve.Tango, TangoPlayFixture.envelope())

    @Test
    fun `a stored Queens solve opened cold offers Post to LinkedIn`() =
        aStoredSolveOffersPost(DailySolve.Queens, QueensPlayFixture.envelope())

    @Test
    fun `a stored Zip solve opened cold offers Post to LinkedIn`() =
        aStoredSolveOffersPost(DailySolve.Zip, ZipPlayFixture.envelope())

    @Test
    fun `a stored Mini Sudoku solve opened cold offers Post to LinkedIn`() =
        aStoredSolveOffersPost(DailySolve.MiniSudoku, MiniSudokuPlayFixture.envelope())

    /** The row's last gate: the stored bytes must open, fold to the solved board and be taken by the game's encoder. */
    @Test
    fun `the replay a Tango solve stores folds to the solved board its LinkedIn encoder takes`() {
        val puzzle = TangoPlayFixture.envelope()
        shellRobot(tabAt(PlayKey), graph = shellGraph()) {
            serveToday(DailySolve.Tango, puzzle)
            solve(DailySolve.Tango)
            val replay = stored(DailySolve.Tango).replay
            val tango = checkNotNull(graph.koin.get<GameRegistry>()[DailySolve.Tango.id]) { "Tango is registered" }

            val timeline = checkNotNull(tango.openTimeline(puzzle, replay)) { "the stored Tango replay does not open" }
            val last = timeline.at(timeline.endMs)
            assertTrue("the replay ends on a solved board", last.facts.solved)
            assertTrue("the last board is complete", (last.session as TangoSession).isComplete)
            assertNotNull("the encoder takes the stored solve", tango.linkedInPlayOf(puzzle, replay))
        }
    }

    /**
     * The landing the widget and the Today tile make: the day's result opened cold, with no game page visited first
     * to sync it. LinkedIn holds the player's own result for the day, so the row says so and never offers Post.
     */
    @Test
    fun `a stored Zip solve on a day LinkedIn already holds says Already on LinkedIn when opened cold`() {
        val game = DailySolve.Zip
        val puzzle = ZipPlayFixture.envelope()
        val solve = storedSolve(game, puzzle)
        shellRobot(tabAt(PlayKey), graph = storedGraph(game, puzzle, solve)) {
            mirrorLinkedInFacts(game)
            linkWithPostingOn()
            linkedInHolds(game, solve)

            openStoredResult(solve)

            awaitIdling { rule.onAllNodes(hasTestTag(POST_ROW) and (hasText("Post to LinkedIn") or hasText("Already on LinkedIn"))).fetchSemanticsNodes().isNotEmpty() }
            rule.onNodeWithTag(POST_ROW).assertTextContains("Already on LinkedIn")
        }
    }

    /** Solves today's [game], whose board is [puzzle], and expects the row on the result the win opens. */
    private fun aSolveOffersPost(game: DailySolve, puzzle: Puzzle) =
        shellRobot(tabAt(PlayKey), graph = linkedGraph()) {
            serveToday(game, puzzle)
            mirrorLinkedInFacts(game)
            linkWithPostingOn()

            solve(game)

            assertOffersPost(game)
        }

    /**
     * A launch that finds today's [game] already solved: the record and the replay of an earlier launch's real
     * solve are stored, the account is linked with posting on, and the result is opened from its key alone.
     */
    private fun aStoredSolveOffersPost(game: DailySolve, puzzle: Puzzle) {
        val solve = storedSolve(game, puzzle)
        shellRobot(tabAt(PlayKey), graph = storedGraph(game, puzzle, solve)) {
            mirrorLinkedInFacts(game)
            linkWithPostingOn()

            openStoredResult(solve)

            assertOffersPost(game)
        }
    }

    /** A linked-LinkedIn shell whose stores hold [solve], the earlier launch's solve of today's [game]. */
    private fun storedGraph(game: DailySolve, puzzle: Puzzle, solve: StoredSolve): E2eKoinGraph {
        val number = (solve.record.puzzleRef.key as PuzzleKey.Daily).puzzleNumber
        return linkedGraph().apply {
            daily.addEntry(DailyEntry(game.id, E2E_TODAY, number, puzzle.variant.value, 0L))
            daily.setCached(game.id, E2E_TODAY, puzzle)
            runBlocking { records.onCompleted(solve.record, solve.replay) }
        }
    }

    /** Opens [solve]'s result from its key alone, as a landing does, and waits for the page. */
    private fun ShellRobot.openStoredResult(solve: StoredSolve) {
        push(ResultKey(solve.record.puzzleRef.gameId.value, "$E2E_TODAY", solve.record.time, solve.record.hints))
        awaitDrained(ROW_TIMEOUT_MS) { shows(RESULT_ROUTE) }
    }

    /** LinkedIn answers [game]'s day with the player's own solved result, as it does for a player who also played there. */
    private fun ShellRobot.linkedInHolds(game: DailySolve, solve: StoredSolve) {
        val account = graph.koin.get<LinkedInAccountSource>() as FakeLinkedInAccountSource
        val number = checkNotNull((solve.record.puzzleRef.key as PuzzleKey.Daily).puzzleNumber) { "$game: the solve has no puzzle number" }
        val typeId = checkNotNull(source(game).linkedInGameTypeId) { "$game has no LinkedIn type" }
        account.respondOwn(LINKEDIN_MEMBER, typeId, number, LinkedInRead.Ok(LinkedInOwnResult.Solved(timeSeconds = (solve.record.time / 1000).toInt(), hintFree = true)))
    }

    /** What a solve leaves on disk: its record and the replay bytes stored with it. */
    private class StoredSolve(val record: CompletionRecord, val replay: SolveReplay)

    /** A real solve of today's [game] by its own board, in a launch of its own: what a later launch finds stored. */
    private fun storedSolve(game: DailySolve, puzzle: Puzzle): StoredSolve {
        var solve: StoredSolve? = null
        shellRobot(tabAt(PlayKey), graph = shellGraph()) {
            serveToday(game, puzzle)
            solve(game)
            solve = stored(game)
        }
        return checkNotNull(solve) { "$game: the solve stored nothing" }
    }

    /** The record and replay this launch's solve of [game] stored. */
    private fun ShellRobot.stored(game: DailySolve): StoredSolve {
        val record = runBlocking { graph.records.observeRecords().first() }.single { it.puzzleRef.gameId == game.id }
        val replay = runBlocking { graph.records.recordReplay(record.puzzleRef) }
        return StoredSolve(record, checkNotNull(replay) { "$game: the solve stored no replay" })
    }

    /** The shell with every LinkedIn edge a fake, so nothing here can reach LinkedIn. */
    private fun linkedGraph(): E2eKoinGraph = shellGraph(
        overrides = module {
            single<LinkedInSessionCookies> { FakeLinkedInSessionCookies(sentinelSession) }
            single<LinkedInAccountSource> { FakeLinkedInAccountSource() }
            single<LinkedInResultPoster> { FakeLinkedInResultPoster() }
            single<LinkedInPhotoSource> { FakeLinkedInPhotoSource() }
        },
    )

    private fun ShellRobot.source(game: DailySolve): DailySource =
        graph.koin.getAll<DailySource>().single { it.gameId == game.id }

    /** Today's [puzzle] as a fetch stores it, under the number [game]'s registered source gives the day. */
    private fun ShellRobot.serveToday(game: DailySolve, puzzle: Puzzle) {
        // The record takes the day's number from its history row, and a solve without a number is never offered.
        val number = checkNotNull(source(game).puzzleNumber(E2E_TODAY)) { "$game has no number for $E2E_TODAY" }
        graph.daily.addEntry(DailyEntry(game.id, E2E_TODAY, number, puzzle.variant.value, 0L))
        // The real repository keeps today's puzzle in its cache; the fake serves it only from here.
        graph.daily.setCached(game.id, E2E_TODAY, puzzle)
    }

    /** What the real repository answers about [game] on LinkedIn: whatever its registered source reports, unset included. */
    private fun ShellRobot.mirrorLinkedInFacts(game: DailySolve) {
        val source = source(game)
        graph.daily.setLinkedInGameType(game.id, source.linkedInGameTypeId)
        graph.daily.setLinkedInGamePath(game.id, source.linkedInGamePath)
        // The day's sync asks the repository for the day's number, as the real one answers from the same source.
        graph.daily.setPuzzleNumbers(game.id, source::puzzleNumber)
    }

    private fun ShellRobot.linkWithPostingOn() {
        graph.connectLinkedIn()
        runBlocking { graph.koin.get<LinkedInLink>().setPosting(consentAtMs = 1L) }
    }

    /** Plays [game]'s board to the win and waits for the result the win opens. */
    private fun ShellRobot.solve(game: DailySolve) {
        val win = with(game) { playToTheLastMove() }
        rule.runOnIdle(win)
        // The win's handoff runs on the main looper, which a frame-only wait never idles.
        awaitDrained(ROW_TIMEOUT_MS) { shows(RESULT_ROUTE) }
    }

    private fun ShellRobot.assertOffersPost(game: DailySolve) {
        try {
            // The row is there but inert (no semantics) until the page's primary resolves; wait for the ready row.
            awaitIdling { rule.onAllNodes(hasTestTag(POST_ROW) and hasText("Post to LinkedIn")).fetchSemanticsNodes().isNotEmpty() }
        } catch (timeout: ComposeTimeoutException) {
            throw AssertionError("$game: the result page offers no Post to LinkedIn row", timeout)
        }
        rule.onNodeWithTag(POST_ROW).assertTextContains("Post to LinkedIn").assertIsEnabled().assertHasClickAction()
    }

    private companion object {
        const val RESULT_ROUTE = "route.result"
        const val POST_ROW = "result.linkedin_post"
        // Wall-clock cap only: the waits above end when the work is done, and the app's own time limits stay still meanwhile.
        const val ROW_TIMEOUT_MS = 30_000L
    }
}
