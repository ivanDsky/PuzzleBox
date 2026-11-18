package com.ivandsky.puzzlebox.compete.duel

import com.ivandsky.puzzlebox.common.dispatcher.DefaultDispatcherProvider
import com.ivandsky.puzzlebox.data.match.LegRef
import com.ivandsky.puzzlebox.data.model.PostState
import com.ivandsky.puzzlebox.data.model.ReceivedPuzzle
import com.ivandsky.puzzlebox.data.model.SharedPuzzleDoc
import com.ivandsky.puzzlebox.data.repository.DailyDay
import com.ivandsky.puzzlebox.designsystem.AccentSlot
import com.ivandsky.puzzlebox.domain.LoadLegPuzzle
import com.ivandsky.puzzlebox.domain.LoadSharedPuzzle
import com.ivandsky.puzzlebox.game.FetchError
import com.ivandsky.puzzlebox.game.GameRegistry
import com.ivandsky.puzzlebox.game.Puzzle
import com.ivandsky.puzzlebox.game.VariantId
import com.ivandsky.puzzlebox.game.editor.Budget
import com.ivandsky.puzzlebox.gameui.GamePlayRegistry
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.model.ShareCode
import com.ivandsky.puzzlebox.testing.repository.FakeDailyRepository
import com.ivandsky.puzzlebox.testing.repository.FakeReceivedPuzzleRepository
import com.ivandsky.puzzlebox.testing.repository.FakeSharedPuzzleRepository
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** A leg's board is its puzzle's givens from the game, kept once settled; a puzzle that is not here, or a game with none, is no board. */
class DuelLegBoardsTest {

    private val game = GameId("Zip")
    private val day = LocalDate.of(2026, 11, 9)
    private val leg = LegRef.Daily(game, day, 580)
    private val puzzle = Puzzle(game, VariantId("v1"), 1, byteArrayOf(7))
    private val daily = FakeDailyRepository()
    private val givens = StubGivens()
    private val received = FakeReceivedPuzzleRepository()
    private val shared = FakeSharedPuzzleRepository().apply { signedInUid = "cleo" }
    private val custom = LegRef.Custom(game, checkNotNull(ShareCode.parse("K7Q2MX")))

    private fun boards(replay: StubGivens?) = DuelLegBoards(
        loadPuzzle = LoadLegPuzzle(
            daily = daily,
            received = received,
            loadShared = LoadSharedPuzzle(
                registry = GameRegistry(emptyList()),
                shared = shared,
                received = received,
                budget = { Budget.within(1_000, System::nanoTime) },
                clock = { Instant.EPOCH },
            ),
        ),
        plays = GamePlayRegistry(listOf(BoardedPlay(game, AccentSlot.Blue, replay))),
        dispatchers = DefaultDispatcherProvider(),
    )

    @Test
    fun `a leg's puzzle gives its givens, asked of the game once however often the page asks`() = runTest {
        daily.setCached(game, day, puzzle)
        val boards = boards(givens)

        val first = boards.of(leg)
        val second = boards.of(leg)

        assertTrue(first is LegBoard.Of)
        assertEquals(first, second)
        assertEquals(listOf(puzzle), givens.asked)
    }

    @Test
    fun `a game that draws no givens has no board`() = runTest {
        daily.setCached(game, day, puzzle)

        assertEquals(LegBoard.None, boards(replay = null).of(leg))
    }

    @Test
    fun `a puzzle that cannot be read now is no board yet, and is asked again`() = runTest {
        daily.setDay(game, day, DailyDay.Failed(FetchError.Unavailable("offline")))
        val boards = boards(givens)
        assertEquals(LegBoard.None, boards.of(leg))

        daily.setDay(game, day, DailyDay.Ready(puzzle, 580))

        assertTrue(boards.of(leg) is LegBoard.Of)
    }

    @Test
    fun `a puzzle that is not out is no board, kept`() = runTest {
        daily.setDay(game, day, DailyDay.Failed(FetchError.NotFound("404")))
        val boards = boards(givens)
        assertEquals(LegBoard.None, boards.of(leg))

        daily.setDay(game, day, DailyDay.Ready(puzzle, 580))

        assertEquals(LegBoard.None, boards.of(leg))
    }

    private suspend fun publish(unpublished: Boolean) {
        shared.publish(
            SharedPuzzleDoc(
                code = custom.code, formatVersion = SharedPuzzleDoc.FORMAT_VERSION, gameId = game.value, variant = "v1",
                shareCodecVersion = 1, payload = byteArrayOf(1), title = "Crown", authorName = "Cleo", note = "",
                authorUid = "cleo", authorTimeMs = 1_000L, authorHints = 0, createdAtMs = 0L, unpublished = false,
                visibility = SharedPuzzleDoc.Visibility.LinkOnly,
            ),
        )
        if (unpublished) shared.unpublish(custom.code)
    }

    private suspend fun saveRow() = received.upsert(
        ReceivedPuzzle(
            code = custom.code, gameId = game, variant = VariantId("v1"), sharedFormatVersion = 1, puzzleBytes = byteArrayOf(7),
            title = "Crown", authorName = "Cleo", note = "", authorTimeMs = 1_000L, authorHints = 0, authorUid = "cleo",
            openedAt = Instant.EPOCH, unpublished = false, postState = PostState.Pending, postAttempts = 0, difficulty = null,
        ),
    )

    @Test
    fun `a friend's puzzle its author took down, never saved here, is taken down, kept`() = runTest {
        publish(unpublished = true)
        val boards = boards(givens)
        assertEquals(LegBoard.TakenDown, boards.of(custom))

        shared.online = false

        assertEquals(LegBoard.TakenDown, boards.of(custom))
    }

    @Test
    fun `a friend's puzzle that cannot be read now is offline, and a retry once back online gives the board`() = runTest {
        shared.online = false
        val boards = boards(givens)
        assertEquals(LegBoard.Offline, boards.of(custom))
        assertEquals(LegBoard.Offline, boards.of(custom))

        saveRow()

        assertTrue(boards.of(custom) is LegBoard.Of)
    }

    @Test
    fun `an offline daily puzzle is no board, not an offline line`() = runTest {
        daily.setDay(game, day, DailyDay.Failed(FetchError.Unavailable("offline")))

        assertEquals(LegBoard.None, boards(givens).of(leg))
    }

    @Test
    fun `a taken-down puzzle already saved here still gives its board`() = runTest {
        publish(unpublished = true)
        saveRow()

        assertTrue(boards(givens).of(custom) is LegBoard.Of)
    }
}
