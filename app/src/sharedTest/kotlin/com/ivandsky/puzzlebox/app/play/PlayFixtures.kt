package com.ivandsky.puzzlebox.app.play

import com.ivandsky.puzzlebox.data.repository.DailyEntry
import com.ivandsky.puzzlebox.data.repository.DailyToday
import com.ivandsky.puzzlebox.model.PuzzleKey
import com.ivandsky.puzzlebox.testing.repository.FakeDailyRepository
import com.ivandsky.puzzlebox.game.Puzzle
import com.ivandsky.puzzlebox.game.VariantId
import com.ivandsky.puzzlebox.games.queens.Cell as QueensCell
import com.ivandsky.puzzlebox.games.queens.QueensDefinition
import com.ivandsky.puzzlebox.games.queens.QueensPuzzle
import com.ivandsky.puzzlebox.games.queens.codec.QueensCodec
import com.ivandsky.puzzlebox.games.queens.queensVariantId
import com.ivandsky.puzzlebox.games.strands.Coord as StrandsCoord
import com.ivandsky.puzzlebox.games.strands.StrandsDefinition
import com.ivandsky.puzzlebox.games.strands.StrandsPuzzle
import com.ivandsky.puzzlebox.games.strands.StrandsWord
import com.ivandsky.puzzlebox.games.strands.codec.StrandsCodec
import com.ivandsky.puzzlebox.games.sudoku.SudokuGames
import com.ivandsky.puzzlebox.games.sudoku.SudokuPuzzle
import com.ivandsky.puzzlebox.games.sudoku.SudokuSamples
import com.ivandsky.puzzlebox.games.sudoku.codec.SudokuCodec
import com.ivandsky.puzzlebox.games.tango.TangoCell
import com.ivandsky.puzzlebox.games.tango.TangoDefinition
import com.ivandsky.puzzlebox.games.tango.TangoEdgeSide
import com.ivandsky.puzzlebox.games.tango.TangoMarker
import com.ivandsky.puzzlebox.games.tango.TangoMarkerKind
import com.ivandsky.puzzlebox.games.tango.TangoPuzzle
import com.ivandsky.puzzlebox.games.tango.TangoPuzzleRef
import com.ivandsky.puzzlebox.games.tango.codec.TangoCodec
import com.ivandsky.puzzlebox.games.tango.engine.TangoUniqueness
import com.ivandsky.puzzlebox.games.zip.Cell
import com.ivandsky.puzzlebox.games.zip.Wall
import com.ivandsky.puzzlebox.games.zip.WallDirection
import com.ivandsky.puzzlebox.games.zip.ZipBoard
import com.ivandsky.puzzlebox.games.zip.ZipDefinition
import com.ivandsky.puzzlebox.games.zip.ZipPuzzle
import com.ivandsky.puzzlebox.games.zip.codec.ZipCodec
import com.ivandsky.puzzlebox.games.zip.engine.ZipVariants
import com.ivandsky.puzzlebox.model.GameId
import java.time.LocalDate

/**
 * Real-game fixtures for the app's play-through tests (the play feature's own tests run
 * on stand-in games). Deterministic 5x5 Zip (mirrors games/zip's ZipTestSupport board):
 * boustrophedon solution; DOWN walls on every row crossing the solution does NOT use, so
 * exactly one full path exists.
 */
object ZipPlayFixture {

    val solution: List<Cell> = listOf(
        0, 1, 2, 3, 4, 9, 8, 7, 6, 5, 10, 11, 12, 13, 14,
        19, 18, 17, 16, 15, 20, 21, 22, 23, 24,
    ).map(::Cell)

    private val wallCells: Set<Int> = (0..19).toSet() - setOf(4, 5, 14, 15)

    val waypoints: List<Cell> = listOf(Cell(0), Cell(12), Cell(24))

    fun board(): ZipBoard =
        ZipBoard(n = 5, walls = wallCells.map { Wall(Cell(it), WallDirection.DOWN) }.toSet(), waypoints = waypoints)

    fun puzzle(): ZipPuzzle =
        ZipPuzzle(variant = ZipVariants.byId(VariantId("zip/n5"))!!.id, board = board(), solution = solution)

    /** Envelope as DailyRepository hands it to the UI (payload = codec bytes). */
    fun envelope(puzzle: ZipPuzzle = puzzle()): Puzzle = Puzzle(
        gameId = ZipDefinition.id,
        variant = puzzle.variant,
        formatVersion = ZipCodec.VERSION,
        payload = ZipCodec.encode(puzzle),
    )

    /** [day]'s captured Zip entry (#592). */
    fun entry(day: LocalDate, puzzleNumber: Int? = 592) =
        DailyEntry(ZipDefinition.id, day, puzzleNumber, "zip/n5", 0L)
}

/** 4×4 Queens daily, regions = rows. */
object QueensPlayFixture {
    fun envelope(): Puzzle = Puzzle(
        gameId = QueensDefinition.id,
        variant = queensVariantId(4),
        formatVersion = QueensCodec.VERSION,
        payload = QueensCodec.encode(
            QueensPuzzle(size = 4, regions = List(16) { it / 4 }, solution = listOf(1, 6, 11, 12).map(::QueensCell)),
        ),
    )
}

// ---------------------------------------------------------------------------
// Tango fixtures (tango-05): the games/tango test suite's proven-unique 6x6 sample,
// re-declared here (test fixtures do not cross module boundaries), gated through the
// A15 solver so a fixture drift fails loudly instead of silently changing semantics.
// ---------------------------------------------------------------------------

/** A uniquely solvable 6x6 Tango daily and its solution. */
object TangoPlayFixture {

    val variant = VariantId("tango/n6")

    /** The bundled fixture's full solution (proven unique by games/tango's suite). */
    val solution: List<TangoCell> = listOf(
        "SSMSMM",
        "SSMSMM",
        "MMSMSS",
        "SSMMSM",
        "MMSSMS",
        "MMSMSS",
    ).flatMap { row -> row.map { if (it == 'S') TangoCell.SUN else TangoCell.MOON } }

    val puzzle: TangoPuzzle = run {
        val p = TangoPuzzle(
            variant = variant,
            n = 6,
            givens = mapOf(
                0 to TangoCell.SUN,
                1 to TangoCell.SUN,
                6 to TangoCell.SUN,
                7 to TangoCell.SUN,
                14 to TangoCell.SUN,
                16 to TangoCell.SUN,
                22 to TangoCell.SUN,
                29 to TangoCell.SUN,
                32 to TangoCell.SUN,
                33 to TangoCell.MOON,
            ),
            markers = listOf(
                TangoMarker(3, TangoEdgeSide.DOWN, TangoMarkerKind.SAME),
                TangoMarker(18, TangoEdgeSide.RIGHT, TangoMarkerKind.SAME),
                TangoMarker(18, TangoEdgeSide.DOWN, TangoMarkerKind.OPPOSITE),
                TangoMarker(34, TangoEdgeSide.RIGHT, TangoMarkerKind.SAME),
            ),
        )
        check(TangoUniqueness.isUniquelySolvable(p)) { "tango fixture must be uniquely solvable (A15)" }
        p
    }

    /** Player-authored cells only (the givens are already on the puzzle). */
    val placements: List<Pair<Int, TangoCell>> =
        (0 until 36).filter { idx -> puzzle.givens[idx] == null }.map { idx -> idx to solution[idx] }

    /** Envelope as DailyRepository hands it to the UI (payload = codec bytes). */
    fun envelope(puzzle: TangoPuzzle = this.puzzle): Puzzle = Puzzle(
        gameId = TangoDefinition.id,
        variant = puzzle.variant,
        formatVersion = TangoCodec.VERSION,
        payload = TangoCodec.encode(puzzle),
    )

    fun ref(day: LocalDate, puzzleNumber: Int? = 301) = TangoPuzzleRef(PuzzleKey.Daily(day, puzzleNumber))

    /** [day]'s captured Tango entry. */
    fun entry(day: LocalDate, puzzleNumber: Int? = 301, fetchedAtMs: Long = 0L) =
        DailyEntry(TangoDefinition.id, day, puzzleNumber, "tango/n6", fetchedAtMs)
}

/** LinkedIn Mini Sudoku #450 "Peanut": 12 givens, one solution. */
object MiniSudokuPlayFixture {

    private fun grid(text: String): List<Int> = text.filterNot { it.isWhitespace() }.map { if (it == '.') 0 else it.digitToInt() }

    val puzzle: SudokuPuzzle = SudokuPuzzle(
        SudokuGames.MINI.variant,
        givens = grid("..3.5. .2.4.6 1..... .....2 2.4.1. .1.3.."),
        solution = grid("463251 521436 142563 356142 234615 615324"),
    )

    /** Envelope as DailyRepository hands it to the UI (payload = codec bytes). */
    fun envelope(): Puzzle = Puzzle(SudokuGames.MINI.id, puzzle.variant.id, SudokuCodec.VERSION, SudokuCodec.encode(puzzle))
}

/**
 * A 4×4 Strands board whose words are its rows: PIES, BAKE (the spangram, edge to edge),
 * TART and CAKE. [rows] in order solve it.
 */
object StrandsPlayFixture {
    val rows: List<String> = listOf("PIES", "BAKE", "TART", "CAKE")

    val puzzle: StrandsPuzzle = run {
        val words = rows.mapIndexed { r, text -> StrandsWord(text, text.indices.map { StrandsCoord(r, it) }) }
        StrandsPuzzle(
            rows = 4,
            cols = 4,
            letters = rows.joinToString(""),
            clue = "Bake sale",
            spangram = words[1],
            themeWords = words - words[1],
            validWords = rows.toSet(),
            constructors = "Tess Tester",
        )
    }

    /** Envelope as DailyRepository hands it to the UI (payload = codec bytes). */
    fun envelope(): Puzzle = StrandsCodec.envelope(puzzle)
}

/** Scripts [game] on this fake: its [today] outcome and its captured days. */
fun FakeDailyRepository.script(
    game: GameId,
    today: DailyToday,
    history: List<DailyEntry> = emptyList(),
): FakeDailyRepository = apply {
    setToday(game, today)
    setHistory(game, history)
}

/** A daily repository serving every game's fixture fresh (the route default). */
fun freshDailyForAllGames(): FakeDailyRepository = FakeDailyRepository()
    .script(ZipDefinition.id, DailyToday.Fresh(ZipPlayFixture.envelope()))
    .script(TangoDefinition.id, DailyToday.Fresh(TangoPlayFixture.envelope()))
    .script(QueensDefinition.id, DailyToday.Fresh(QueensPlayFixture.envelope()))
    .script(SudokuGames.MINI.id, DailyToday.Fresh(MiniSudokuPlayFixture.envelope()))
    // The debug-only 9x9 (ADR-0015): the hub's sync asks every registered game for today.
    .script(SudokuGames.CLASSIC.id, DailyToday.Fresh(classicSudokuEnvelope()))
    .script(StrandsDefinition.id, DailyToday.Fresh(StrandsPlayFixture.envelope()))

/** The bundled 9x9 board as DailyRepository hands it to the UI. */
private fun classicSudokuEnvelope(): Puzzle =
    Puzzle(SudokuGames.CLASSIC.id, SudokuSamples.classic.variant.id, SudokuCodec.VERSION, SudokuCodec.encode(SudokuSamples.classic))
