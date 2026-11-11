package com.ivandsky.puzzlebox.games.sudoku

import com.ivandsky.puzzlebox.game.Cadence
import com.ivandsky.puzzlebox.game.DailySource
import com.ivandsky.puzzlebox.game.DecodeError
import com.ivandsky.puzzlebox.game.DecodeResult
import com.ivandsky.puzzlebox.game.FeedTransport
import com.ivandsky.puzzlebox.game.FetchError
import com.ivandsky.puzzlebox.game.FetchResult
import com.ivandsky.puzzlebox.game.GamePlugin
import com.ivandsky.puzzlebox.game.Move
import com.ivandsky.puzzlebox.game.Puzzle
import com.ivandsky.puzzlebox.game.SessionBlob
import com.ivandsky.puzzlebox.games.sudoku.codec.SudokuCodec
import com.ivandsky.puzzlebox.games.sudoku.codec.SudokuSessionCodec
import com.ivandsky.puzzlebox.games.sudoku.engine.hints.SudokuHint
import com.ivandsky.puzzlebox.games.sudoku.engine.hints.SudokuHints
import com.ivandsky.puzzlebox.games.sudoku.render.SudokuRendererModel
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.model.PuzzleKey
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.koin.core.qualifier.qualifier
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class SudokuPluginTest {

    private val plugin = sudokuPlugin(SudokuGames.MINI)
    private val puzzle = KnownSolutions.peanut450
    private val payload = SudokuCodec.encode(puzzle)
    private val date = LocalDate.of(2026, 11, 4)

    @Test
    fun `the two games have their ids, names, variants and the Pacific cadence`() {
        assertEquals(GameId("minisudoku"), SudokuGames.MINI.id)
        assertEquals("Mini Sudoku", SudokuGames.MINI.displayName)
        assertSame(SudokuVariants.MINI, SudokuGames.MINI.variant)
        assertEquals(GameId("sudoku"), SudokuGames.CLASSIC.id)
        assertEquals("Sudoku", SudokuGames.CLASSIC.displayName)
        assertSame(SudokuVariants.CLASSIC, SudokuGames.CLASSIC.variant)
        assertEquals(Cadence.Daily(ZoneId.of("America/Los_Angeles")), SudokuGames.MINI.cadence)
        assertSame(SudokuGames.MINI, plugin.definition)
    }

    @Test
    fun `decode wraps the original bytes and encode is canonical`() {
        val envelope = (SudokuGames.MINI.decode(payload) as DecodeResult.Ok).value
        assertEquals(GameId("minisudoku"), envelope.gameId)
        assertEquals(SudokuVariants.MINI.id, envelope.variant)
        assertSame(payload, envelope.payload)
        assertArrayEquals(payload, SudokuGames.MINI.encode(envelope))
        assertThrows(IllegalArgumentException::class.java) {
            SudokuGames.CLASSIC.encode(envelope)
        }
    }

    @Test
    fun `a 9x9 payload is refused by the mini game`() {
        val classic = SudokuCodec.encode(KnownSolutions.classicSample)
        assertEquals(DecodeResult.Err(DecodeError.VariantSizeMismatch("sudoku/6x6", 6, 9)), SudokuGames.MINI.decode(classic))
        val envelope = Puzzle(SudokuGames.MINI.id, SudokuVariants.CLASSIC.id, SudokuCodec.VERSION, classic)
        assertTrue(plugin.initialSession(envelope) is DecodeResult.Err)
    }

    @Test
    fun `initial session starts fresh and refuses a foreign envelope`() {
        val envelope = Puzzle(SudokuGames.MINI.id, SudokuVariants.MINI.id, SudokuCodec.VERSION, payload)
        assertEquals(DecodeResult.Ok(SudokuSession(ref = null, puzzle = puzzle)), plugin.initialSession(envelope))
        val foreign = Puzzle(GameId("queens"), SudokuVariants.MINI.id, SudokuCodec.VERSION, payload)
        assertTrue((plugin.initialSession(foreign) as DecodeResult.Err).error is DecodeError.Malformed)
    }

    @Test
    fun `replay seeds a fresh session carrying the day's ref`() {
        val envelope = Puzzle(SudokuGames.MINI.id, SudokuVariants.MINI.id, SudokuCodec.VERSION, payload)
        val key = PuzzleKey.Daily(date, 450)
        val blob = (plugin.playAgainSession(envelope, key) as DecodeResult.Ok).value
        val decoded = SessionBlob.decode(blob)!!
        assertEquals(SudokuGames.MINI.id, decoded.game)
        val session = (SudokuSessionCodec.deserialize(decoded.session) as DecodeResult.Ok).value
        assertEquals(SudokuSession(ref = SudokuPuzzleRef(SudokuGames.MINI.id, PuzzleKey.Daily(date, 450)), puzzle = puzzle), session)
        assertEquals(SudokuPuzzleRef(SudokuGames.MINI.id, PuzzleKey.Daily(date, 450)), plugin.refOf(key))
    }

    @Test
    fun `a custom key replays carrying the friend's puzzle identity`() {
        val envelope = Puzzle(SudokuGames.MINI.id, SudokuVariants.MINI.id, SudokuCodec.VERSION, payload)
        val custom = PuzzleKey.Custom("K7Q2MX")
        val decoded = SessionBlob.decode((plugin.playAgainSession(envelope, custom) as DecodeResult.Ok).value)!!
        val session = (SudokuSessionCodec.deserialize(decoded.session) as DecodeResult.Ok).value
        assertEquals(SudokuPuzzleRef(SudokuGames.MINI.id, custom), session.ref)
        assertEquals(SudokuPuzzleRef(SudokuGames.MINI.id, custom), plugin.refOf(custom))
    }

    @Test
    fun `validate gives typed verdicts for every move`() {
        val state = plugin.stateOf(SudokuSession(ref = null, puzzle = puzzle))
        assertEquals(SudokuValidation.Valid, plugin.rules.validate(SudokuMove.Place(0, 6), state))
        assertEquals(SudokuValidation.GivenCell, plugin.rules.validate(SudokuMove.Place(2, 1), state))
        assertEquals(SudokuValidation.OutOfRange, plugin.rules.validate(SudokuMove.Place(36, 1), state))
        assertEquals(SudokuValidation.OutOfRange, plugin.rules.validate(SudokuMove.Place(0, 7), state))
        assertEquals(SudokuValidation.OutOfRange, plugin.rules.validate(SudokuMove.ToggleNote(0, 0), state))
        assertEquals(SudokuValidation.GivenCell, plugin.rules.validate(SudokuMove.Erase(2), state))
        assertEquals(SudokuValidation.Valid, plugin.rules.validate(SudokuMove.Clear, state))
        assertEquals(SudokuValidation.OutOfRange, plugin.rules.validate(object : Move {}, state))
        val solved = SudokuState(puzzle, SudokuBoard(puzzle.geometry, puzzle.solution.map { SudokuCell.Entry(it) }))
        assertEquals(SudokuValidation.Complete, plugin.rules.validate(SudokuMove.Place(0, 4), solved))
    }

    @Test
    fun `hint climbs the ladder and renders its target cell`() {
        val state = plugin.stateOf(SudokuSession(ref = null, puzzle = puzzle))
        val hint = plugin.rules.hint(state)
        assertEquals(SudokuHints().hint(puzzle, state.board), hint)
        val place = hint as SudokuHint.Place
        assertEquals(puzzle.solution[place.cell], place.digit)
        val model = plugin.rendererModel(state, hint, null)
        assertEquals(place.cell, model.hintCell)
        assertEquals(SudokuRendererModel.Status.IDLE, model.status)
        assertEquals(KnownSolutions.PEANUT_450_PRESETS.toSet(), model.givens)
        assertEquals(puzzle.givens, model.digits)
    }

    @Test
    fun `the renderer hides notes under an entry and reports progress`() {
        val board = SudokuBoard.of(puzzle).with(0, SudokuCell.Entry(4, 0b11)).with(1, SudokuCell.Notes(0b100))
        val model = SudokuRendererModel.of(SudokuState(puzzle, board), null)
        assertEquals(0, model.notes[0])
        assertEquals(0b100, model.notes[1])
        assertEquals(4, model.digits[0])
        assertNull(model.hintCell)
        assertEquals(SudokuRendererModel.Status.PLAYING, model.status)
        val solved = SudokuBoard(puzzle.geometry, puzzle.solution.map { SudokuCell.Entry(it) })
        assertEquals(SudokuRendererModel.Status.COMPLETE, SudokuRendererModel.of(SudokuState(puzzle, solved), null).status)
        assertEquals(SudokuHint.None, SudokuGameRules.hint(SudokuState(puzzle, solved)))
    }

    @Test
    fun `score records completed daily sessions with the variant in the stats blob`() {
        val ref = SudokuPuzzleRef(SudokuGames.MINI.id, PuzzleKey.Daily(date, 450))
        val done = SudokuSession(ref = ref, puzzle = puzzle, completedAt = Instant.EPOCH, elapsedMs = 61_000, hintsUsed = 1)
        val record = plugin.score.completion(done)!!
        assertEquals(ref, record.puzzleRef)
        assertEquals(61_000L, record.time)
        assertEquals(1, record.hints)
        assertEquals(1, record.stats.schemaVersion)
        assertEquals("""{"v":1,"variant":"sudoku/6x6"}""", record.stats.blob)
        assertNull(plugin.score.completion(done.copy(completedAt = null)))
        assertNull(plugin.score.completion(done.copy(ref = null)))
    }

    @Test
    fun `the koin module registers the plugin and the daily source under minisudoku`() {
        val koin = koinApplication {
            modules(
                miniSudokuModule,
                module { single<FeedTransport> { FeedTransport { FetchResult.Err(FetchError.Unavailable()) } } },
            )
        }.koin
        assertEquals(listOf(GameId("minisudoku")), koin.getAll<DailySource>().map { it.gameId })
        val registered = koin.get<GamePlugin<*, *, *>>(qualifier("minisudoku"))
        assertSame(SudokuGames.MINI, registered.definition)
        assertEquals(listOf(SudokuGames.MINI.id), koin.getAll<GamePlugin<*, *, *>>().map { it.definition.id })
    }

    @Test
    fun `both games' plugins carry an editor of their own game`() {
        listOf(SudokuGames.MINI, SudokuGames.CLASSIC).forEach { definition ->
            assertEquals(definition.id, sudokuPlugin(definition).editor?.gameId)
        }
    }
}
