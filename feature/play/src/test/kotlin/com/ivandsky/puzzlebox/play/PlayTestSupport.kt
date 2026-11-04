package com.ivandsky.puzzlebox.play

import com.ivandsky.puzzlebox.data.repository.CompletionRepository
import com.ivandsky.puzzlebox.data.repository.DailyEntry
import com.ivandsky.puzzlebox.data.repository.DailyToday
import com.ivandsky.puzzlebox.data.testing.FakeDailyRepository
import com.ivandsky.puzzlebox.game.CompletionRecord
import com.ivandsky.puzzlebox.game.Puzzle
import com.ivandsky.puzzlebox.game.PuzzleRef
import com.ivandsky.puzzlebox.game.VariantId
import com.ivandsky.puzzlebox.games.queens.QueensDefinition
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
import com.ivandsky.puzzlebox.games.zip.ZipPuzzleRef
import com.ivandsky.puzzlebox.games.zip.ZipSession
import com.ivandsky.puzzlebox.games.zip.codec.ZipCodec
import com.ivandsky.puzzlebox.games.zip.engine.ZipVariants
import com.ivandsky.puzzlebox.model.GameId
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/**
 * Deterministic 5x5 fixture (hand-verifiable, mirrors games/zip's ZipTestSupport board):
 * boustrophedon solution; DOWN walls on every row crossing the solution does NOT use, so
 * exactly one full path exists.
 */
object PlayTestSupport {

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

    /** The board with wall (3,DOWN) removed: [0,1,2,3,8] becomes a provably dead trace
     *  (the 8-entry pocket strands the rest), while [0,1,2,3] still completes. */
    fun branchyPuzzle(): ZipPuzzle {
        val walls = board().walls - Wall(Cell(3), WallDirection.DOWN)
        return ZipPuzzle(variant = ZipVariants.byId(VariantId("zip/n5"))!!.id, board = ZipBoard(5, walls, waypoints), solution = solution)
    }

    /** Envelope as DailyRepository hands it to the UI (payload = codec bytes). */
    fun envelope(puzzle: ZipPuzzle = puzzle()): Puzzle = Puzzle(
        gameId = ZipDefinition.id,
        variant = puzzle.variant,
        formatVersion = ZipCodec.VERSION,
        payload = ZipCodec.encode(puzzle),
    )

    /** Valid, provably dead trace on [branchyPuzzle]. */
    fun deadTrace(): List<Cell> = listOf(0, 1, 2, 3, 8).map(::Cell)

    fun path(vararg idx: Int): List<Cell> = idx.map(::Cell)

    fun ref(day: LocalDate) = ZipPuzzleRef(day, 592)

    fun session(ref: ZipPuzzleRef? = null) = ZipSession(ref = ref, puzzle = puzzle())

    /** [day]'s captured Zip entry (#592). */
    fun entry(day: LocalDate, puzzleNumber: Int? = 592) =
        DailyEntry(ZipDefinition.id, day, puzzleNumber, "zip/n5", 0L)

    /** A daily repository scripted for Zip only: [result] and its captured days. */
    fun daily(
        result: DailyToday = DailyToday.Fresh(envelope()),
        history: List<DailyEntry> = emptyList(),
    ): FakeDailyRepository = FakeDailyRepository().script(ZipDefinition.id, result, history)
}

// ---------------------------------------------------------------------------
// Tango fixtures (tango-05): the games/tango test suite's proven-unique 6x6 sample,
// re-declared here (test fixtures do not cross module boundaries), gated through the
// A15 solver so a fixture drift fails loudly instead of silently changing semantics.
// ---------------------------------------------------------------------------

/** Tango side of [PlayTestSupport]: a uniquely solvable 6x6 daily and its solution. */
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

    /** Decodable but NOT uniquely solvable — the A15 gate must refuse it. */
    val ambiguous: TangoPuzzle = TangoPuzzle(
        variant = variant,
        n = 6,
        givens = emptyMap(),
        markers = emptyList(),
    )

    fun ref(day: LocalDate, puzzleNumber: Int? = 301) = TangoPuzzleRef(day, puzzleNumber)

    /** [day]'s captured Tango entry. */
    fun entry(day: LocalDate, puzzleNumber: Int? = 301, fetchedAtMs: Long = 0L) =
        DailyEntry(TangoDefinition.id, day, puzzleNumber, "tango/n6", fetchedAtMs)

    /** A daily repository scripted for Tango only: [result] and its captured days. */
    fun daily(
        result: DailyToday = DailyToday.Fresh(envelope()),
        history: List<DailyEntry> = emptyList(),
    ): FakeDailyRepository = FakeDailyRepository().script(TangoDefinition.id, result, history)
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

/** A daily repository serving every game's fixture fresh (the flow/route default). */
fun freshDailyForAllGames(): FakeDailyRepository = FakeDailyRepository()
    .script(ZipDefinition.id, DailyToday.Fresh(PlayTestSupport.envelope()))
    .script(TangoDefinition.id, DailyToday.Fresh(TangoPlayFixture.envelope()))
    .script(QueensDefinition.id, DailyToday.Fresh(QueensPlayFixtures.envelope()))

/**
 * In-memory [CompletionRepository] fake over the REAL :core:data interface, with a
 * [fail] switch so the write-failure path is drivable. A4 stamping semantics live in
 * the Room implementation — this fake stores verbatim what callers write.
 */
class FakeCompletionRepository : CompletionRepository {

    var fail = false

    private val all = MutableStateFlow<List<CompletionRecord>>(emptyList())

    override suspend fun onCompleted(record: CompletionRecord) {
        if (fail) throw IllegalStateException("disk full")
        all.update { it + record }
    }

    override fun observeRecords(): Flow<List<CompletionRecord>> = all

    override fun observeReplays(puzzleRef: PuzzleRef): Flow<List<CompletionRecord>> =
        all.map { rows -> rows.filter { it.puzzleRef == puzzleRef } }
}
