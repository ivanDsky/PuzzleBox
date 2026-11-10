package com.ivandsky.puzzlebox.games.queens

import com.ivandsky.puzzlebox.game.PuzzleRef
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.game.VariantId
import com.ivandsky.puzzlebox.model.PuzzleKey

/** Row-major board cell, `0 until size*size` (zip precedent: idx = row*size + col). */
@JvmInline
value class Cell(val idx: Int) {
    override fun toString(): String = "Cell($idx)"
}

/**
 * A Queens puzzle: board [size], per-cell region id, and the queens-per-unit count.
 *
 * PINNED INTERFACE (queens-01, repeated verbatim by queens-02/03/04/06):
 * `QueensPuzzle(size, regions, queensPerUnit = 1)`. [regions] is row-major, size*size
 * entries, region ids `0 until size` — exactly [size] regions on a size×size board
 * (research 01). [queensPerUnit] is the A9 parameter: N queens per row/column/region is
 * engine-level configuration, never a separate code path (Star Battle = N≥2, adjacency
 * detection is N-independent).
 *
 * [solution] is the official shipped solution — wire queens carry one (research 02) — as
 * size*queensPerUnit row-major cell indices, or null when absent. It is the A13 oracle
 * INPUT (independent solve must match it) and the hint engine's source; play state never
 * exposes it. It rides the value type so decode→encode is lossless over the full wire
 * content; the default keeps the pinned three-argument constructor intact.
 */
data class QueensPuzzle(
    val size: Int,
    val regions: List<Int>,
    val queensPerUnit: Int = 1,
    val solution: List<Cell>? = null,
    /** Bytes a newer codec version appended (A19): preserved verbatim so decode∘encode
     *  stays lossless across versions. Defaulted — the pinned constructor shape holds. */
    val unknownTail: ByteArray = ByteArray(0),
) {
    init {
        require(size > 0) { "size must be positive, got $size" }
        require(regions.size == size * size) {
            "regions must carry size*size=${size * size} entries, got ${regions.size}"
        }
        require(queensPerUnit > 0) { "queensPerUnit must be positive, got $queensPerUnit" }
        val bad = regions.firstOrNull { it !in 0 until size }
        require(bad == null) { "region id $bad outside 0 until $size" }
        if (solution != null) {
            require(solution.size == size * queensPerUnit) {
                "solution must place size*queensPerUnit=${size * queensPerUnit} queens, got ${solution.size}"
            }
            require(solution.all { it.idx in 0 until size * size }) { "solution cell out of range" }
            require(solution.map { it.idx }.toSet().size == solution.size) { "duplicate solution cell" }
        }
    }

    val cellCount: Int = size * size

    /**
     * Cell indices (ascending) of each row, column and region, derived once per puzzle for the
     * hint rungs. Body members: not constructor properties, so [equals], [hashCode], [copy] and
     * the codec never see them.
     */
    val rowCells: List<List<Int>> by lazy { List(size) { row -> List(size) { row * size + it } } }
    val columnCells: List<List<Int>> by lazy { List(size) { col -> List(size) { it * size + col } } }
    val regionCells: List<List<Int>> by lazy {
        val cells = List(size) { ArrayList<Int>() }
        for (cell in regions.indices) cells[regions[cell]] += cell
        cells
    }

    fun rowOf(cell: Cell): Int = cell.idx / size
    fun colOf(cell: Cell): Int = cell.idx % size
    fun regionOf(cell: Cell): Int = regions[cell.idx]

    /** The queen's row/column/region id by cell index (renderer + rules seam). */
    fun regionAt(idx: Int): Int = regions[idx]

    // Array-aware structural equality (zip ZipPuzzle precedent).
    override fun equals(other: Any?): Boolean =
        other is QueensPuzzle &&
            size == other.size &&
            regions == other.regions &&
            queensPerUnit == other.queensPerUnit &&
            solution == other.solution &&
            unknownTail.contentEquals(other.unknownTail)

    override fun hashCode(): Int {
        var h = size
        h = 31 * h + regions.hashCode()
        h = 31 * h + queensPerUnit
        h = 31 * h + (solution?.hashCode() ?: 0)
        h = 31 * h + unknownTail.contentHashCode()
        return h
    }
}

/**
 * Queens puzzle identity (ADR-0018, spec A11): a LinkedIn daily (day + puzzle number, null
 * for undated boards) or a friend's custom puzzle. Codec bytes stay identity-free.
 */
data class QueensPuzzleRef(override val key: PuzzleKey) : PuzzleRef {
    override val gameId: GameId get() = QueensDefinition.id
}

/** The variant id for a board size, e.g. "queens/n8". Single naming seam for all modules. */
fun queensVariantId(size: Int): VariantId = VariantId("queens/n$size")
