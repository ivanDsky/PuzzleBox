package com.ivandsky.puzzlebox.app.play

import com.ivandsky.puzzlebox.app.DrainTestApplication
import com.ivandsky.puzzlebox.game.Puzzle
import com.ivandsky.puzzlebox.game.today
import com.ivandsky.puzzlebox.games.queens.QueensDefinition
import com.ivandsky.puzzlebox.games.strands.StrandsDefinition
import com.ivandsky.puzzlebox.games.sudoku.SudokuGames
import com.ivandsky.puzzlebox.games.tango.TangoDefinition
import com.ivandsky.puzzlebox.games.zip.ZipDefinition
import com.ivandsky.puzzlebox.gameui.PlayBoardStatus
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.play.ActivePlay
import com.ivandsky.puzzlebox.testing.repository.FakeDailyRepository
import java.time.LocalDate
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.stopKoin
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Over every real game's controller: a stored daily is ready at once, before its refresh has answered, and
 * the refresh runs behind it (A3). The swap and its player-input latch are pinned over the stand-in games in
 * feature:play:impl, where a puzzle can differ without a game-specific codec.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = DrainTestApplication::class)
class RealGamesBoardFirstTest {

    private val day: LocalDate = TangoDefinition.cadence.today()
    private var graph: RealGamesGraph? = null

    @Before
    fun clearKoin() {
        stopKoin()
    }

    @After
    fun tearDown() {
        graph?.stop()
    }

    /** [game]'s stored [puzzle] with its refresh held: the board is Ready, and the refresh is out but unanswered. */
    private fun assertReadyBeforeRefresh(game: GameId, puzzle: Puzzle) {
        val daily = FakeDailyRepository().apply {
            setCached(game, day, puzzle)
            setToday(game, puzzle)
        }
        val gate = daily.holdRefresh(game)
        val play: ActivePlay = RealGamesGraph(daily, day).also { graph = it }.apply { start() }.open(game)

        awaitUntil("${game.value} refresh started") { daily.todayCalls(game) == 1 }
        awaitUntil("${game.value} ready") { play.board.value.status is PlayBoardStatus.Ready }
        assertEquals(PlayBoardStatus.Ready, play.board.value.status)
        assertTrue("the refresh is still held", !gate.isCompleted)
        gate.complete(Unit)
        assertEquals(1, daily.todayCalls(game))
    }

    @Test fun `zip shows its stored board before the refresh answers`() = assertReadyBeforeRefresh(ZipDefinition.id, ZipPlayFixture.envelope())

    @Test fun `tango shows its stored board before the refresh answers`() = assertReadyBeforeRefresh(TangoDefinition.id, TangoPlayFixture.envelope())

    @Test fun `queens shows its stored board before the refresh answers`() = assertReadyBeforeRefresh(QueensDefinition.id, QueensPlayFixture.envelope())

    @Test fun `mini sudoku shows its stored board before the refresh answers`() = assertReadyBeforeRefresh(SudokuGames.MINI.id, MiniSudokuPlayFixture.envelope())

    @Test fun `strands shows its stored board before the refresh answers`() = assertReadyBeforeRefresh(StrandsDefinition.id, StrandsPlayFixture.envelope())
}
