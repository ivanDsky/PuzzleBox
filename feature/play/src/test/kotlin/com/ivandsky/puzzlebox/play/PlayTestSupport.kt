package com.ivandsky.puzzlebox.play

import com.ivandsky.puzzlebox.data.repository.CompletionRepository
import com.ivandsky.puzzlebox.data.repository.ZipDailyEntry
import com.ivandsky.puzzlebox.data.repository.ZipDailyRepository
import com.ivandsky.puzzlebox.data.repository.ZipToday
import com.ivandsky.puzzlebox.game.CompletionRecord
import com.ivandsky.puzzlebox.game.Puzzle
import com.ivandsky.puzzlebox.game.PuzzleRef
import com.ivandsky.puzzlebox.game.VariantId
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

    /** Envelope as ZipDailyRepository hands it to the UI (payload = codec bytes). */
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
}

/** Scriptable ZipDailyRepository fake for the play state tests. */
class FakeZipDailyRepository(
    private var todayResult: ZipToday,
    private val history: List<ZipDailyEntry> = emptyList(),
) : ZipDailyRepository {

    var todayCalls = 0
        private set

    fun setToday(result: ZipToday) {
        todayResult = result
    }

    override suspend fun today(): ZipToday {
        todayCalls++
        return todayResult
    }

    override suspend fun cached(day: LocalDate): Puzzle? = null

    override fun observeHistory(): Flow<List<ZipDailyEntry>> = kotlinx.coroutines.flow.flowOf(history)
}

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
