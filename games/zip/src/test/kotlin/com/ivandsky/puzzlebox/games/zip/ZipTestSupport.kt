package com.ivandsky.puzzlebox.games.zip

import com.ivandsky.puzzlebox.game.VariantId
import com.ivandsky.puzzlebox.games.zip.engine.ZipGenerator
import com.ivandsky.puzzlebox.games.zip.engine.ZipVariant
import com.ivandsky.puzzlebox.games.zip.engine.ZipVariants
import kotlin.random.Random

/** Shared deterministic boards for the Zip test inventory. */
object ZipTestSupport {

    fun variantOf(size: Int): ZipVariant = ZipVariants.byId(VariantId("zip/n$size"))!!

    /**
     * Forced-uniqueness 5x5 board: boustrophedon solution S, DOWN walls on every
     * row-crossing except the ones S uses. Exactly one full path exists (rows become
     * one-way corridors), so solver/rules claims here are hand-verifiable, not just
     * solver-verified — the mutation falsification anchor.
     */
    val N5: Int = 5
    val n5Solution: List<Cell> = listOf(
        0, 1, 2, 3, 4, 9, 8, 7, 6, 5, 10, 11, 12, 13, 14,
        19, 18, 17, 16, 15, 20, 21, 22, 23, 24,
    ).map(::Cell)
    val n5WallCells: Set<Int> = setOf(0, 1, 2, 3, 6, 7, 8, 9, 10, 11, 12, 13, 16, 17, 18, 19)

    fun n5Board(waypoints: List<Cell> = listOf(Cell(0), Cell(12), Cell(24))): ZipBoard =
        ZipBoard(
            n = N5,
            walls = n5WallCells.map { Wall(Cell(it), WallDirection.DOWN) }.toSet(),
            waypoints = waypoints,
        )

    fun n5Puzzle(waypoints: List<Cell> = listOf(Cell(0), Cell(12), Cell(24))): ZipPuzzle =
        ZipPuzzle(variantOf(N5).id, n5Board(waypoints), n5Solution)

    /**
     * Same board with wall (3,DOWN) removed, opening a branch: [0,1,2,3,8] is provably
     * dead (region cells become unreachable), while [0,1,2,3] still completes.
     */
    fun n5BranchyPuzzle(): ZipPuzzle {
        val walls = n5Board().walls - Wall(Cell(3), WallDirection.DOWN)
        return ZipPuzzle(
            variantOf(N5).id,
            ZipBoard(N5, walls, listOf(Cell(0), Cell(12), Cell(24))),
            n5Solution,
        )
    }

    /** Open 5x5 (no walls): many Hamiltonian paths — proves the solver is not over-pruned. */
    fun n5OpenPuzzle(): ZipPuzzle = ZipPuzzle(
        variantOf(N5).id,
        ZipBoard(N5, emptySet(), listOf(Cell(0), Cell(24))),
        null,
    )

    fun seededGenerator(seed: Int): ZipGenerator = ZipGenerator(Random(seed))

    fun path(vararg idx: Int): List<Cell> = idx.map(::Cell)
}

fun path(vararg idx: Int): List<Cell> = idx.map(::Cell)

fun <T> com.ivandsky.puzzlebox.game.DecodeResult<T>.getOrThrow(): T = when (this) {
    is com.ivandsky.puzzlebox.game.DecodeResult.Ok -> value
    is com.ivandsky.puzzlebox.game.DecodeResult.Err -> throw AssertionError("decode failed: $error")
}
