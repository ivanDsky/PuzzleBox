package com.ivandsky.puzzlebox.history

import com.ivandsky.puzzlebox.data.repository.DailyEntry
import com.ivandsky.puzzlebox.game.CompletionRecord
import com.ivandsky.puzzlebox.game.Puzzle
import com.ivandsky.puzzlebox.game.RecordStats
import com.ivandsky.puzzlebox.game.VariantId
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
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * Deterministic 5x5 fixture (hand-verifiable, mirrors the games/zip and play fixtures):
 * boustrophedon solution; DOWN walls on every row crossing the solution does NOT use,
 * so exactly one full path exists.
 */
object HistoryTestSupport {

    const val PUZZLE_NUMBER = 592

    val solution: List<Cell> = listOf(
        0, 1, 2, 3, 4, 9, 8, 7, 6, 5, 10, 11, 12, 13, 14,
        19, 18, 17, 16, 15, 20, 21, 22, 23, 24,
    ).map(::Cell)

    private val wallCells: Set<Int> = (0..19).toSet() - setOf(4, 5, 14, 15)

    private val waypoints: List<Cell> = listOf(Cell(0), Cell(12), Cell(24))

    fun puzzle(): ZipPuzzle =
        ZipPuzzle(
            variant = ZipVariants.byId(VariantId("zip/n5"))!!.id,
            board = ZipBoard(n = 5, walls = wallCells.map { Wall(Cell(it), WallDirection.DOWN) }.toSet(), waypoints = waypoints),
            solution = solution,
        )

    /** Envelope as DailyRepository hands it to the UI (payload = codec bytes). */
    fun envelope(puzzle: ZipPuzzle = puzzle()): Puzzle = Puzzle(
        gameId = ZipDefinition.id,
        variant = puzzle.variant,
        formatVersion = ZipCodec.VERSION,
        payload = ZipCodec.encode(puzzle),
    )

    fun entry(day: LocalDate, puzzleNumber: Int? = PUZZLE_NUMBER, fetchedAtMs: Long = 0L) = DailyEntry(
        gameId = ZipDefinition.id,
        day = day,
        puzzleNumber = puzzleNumber,
        variant = "zip/n5",
        fetchedAtMs = fetchedAtMs,
    )

    fun record(day: LocalDate, timeMs: Long = 95_000, hints: Int = 2) = CompletionRecord(
        puzzleRef = ZipPuzzleRef(day, PUZZLE_NUMBER),
        time = timeMs,
        hints = hints,
        stats = RecordStats(schemaVersion = 1, blob = """{"v":1,"mistakes":0}"""),
    )

    /** Fresh ZipSession the replay blob must carry: this day's ref, empty path. */
    fun expectedSession(day: LocalDate) = ZipSession(
        ref = ZipPuzzleRef(day, PUZZLE_NUMBER),
        puzzle = puzzle(),
    )

    // ------------------------------------------------------------------
    // Tango fixtures (tango-05): the games/tango suite's proven-unique 6x6
    // sample, re-declared (test fixtures do not cross module boundaries) and
    // gated through the A15 solver so drift fails loudly.
    // ------------------------------------------------------------------

    const val TANGO_PUZZLE_NUMBER = 301

    val tangoVariant = VariantId("tango/n6")

    val tangoSolution: List<TangoCell> = listOf(
        "SSMSMM",
        "SSMSMM",
        "MMSMSS",
        "SSMMSM",
        "MMSSMS",
        "MMSMSS",
    ).flatMap { row -> row.map { if (it == 'S') TangoCell.SUN else TangoCell.MOON } }

    val tangoPuzzle: TangoPuzzle = run {
        val p = TangoPuzzle(
            variant = tangoVariant,
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

    /** Decodable but NOT uniquely solvable — the A15 gate must refuse it. */
    val ambiguousTangoPuzzle = TangoPuzzle(variant = tangoVariant, n = 6, givens = emptyMap(), markers = emptyList())

    /** Envelope as DailyRepository hands it to the UI (payload = codec bytes). */
    fun tangoEnvelope(puzzle: TangoPuzzle = tangoPuzzle): Puzzle = Puzzle(
        gameId = TangoDefinition.id,
        variant = puzzle.variant,
        formatVersion = TangoCodec.VERSION,
        payload = TangoCodec.encode(puzzle),
    )

    fun tangoEntry(day: LocalDate, puzzleNumber: Int? = TANGO_PUZZLE_NUMBER, fetchedAtMs: Long = 0L) = DailyEntry(
        gameId = TangoDefinition.id,
        day = day,
        puzzleNumber = puzzleNumber,
        variant = "tango/n6",
        fetchedAtMs = fetchedAtMs,
    )

    fun tangoRecord(day: LocalDate, timeMs: Long = 61_000, hints: Int = 0) = CompletionRecord(
        puzzleRef = TangoPuzzleRef(day, TANGO_PUZZLE_NUMBER),
        time = timeMs,
        hints = hints,
        stats = RecordStats(schemaVersion = 1, blob = """{"v":1,"mistakes":0}"""),
    )
}

/** Epoch-ms for a day's start (UTC) — stable "captured" timestamps in tests. */
fun fetchedAtMs(day: LocalDate): Long = day.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
