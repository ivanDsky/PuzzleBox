package com.ivandsky.puzzlebox.games.tango

import com.ivandsky.puzzlebox.game.PuzzleRef
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.game.VariantId

/** The two-valued Tango cell: sun or moon (A11). */
enum class TangoCell { SUN, MOON;

    companion object {
        /** Wire encoding (TangoCodec v1). */
        fun fromByte(b: Int): TangoCell? = when (b) {
            1 -> SUN
            2 -> MOON
            else -> null
        }
    }

    fun toByte(): Int = ordinal + 1
}

/** Orientation of an interior edge marker, matching EdgeSide semantics: toward the RIGHT or DOWN neighbor. */
enum class TangoEdgeSide { RIGHT, DOWN;

    companion object {
        fun fromByte(b: Int): TangoEdgeSide? = when (b) {
            0 -> RIGHT
            1 -> DOWN
            else -> null
        }
    }

    fun toByte(): Int = ordinal
}

/** A non-interactive `=`/`×` constraint on the edge between [cell] and its [side] neighbor (A12). */
data class TangoMarker(
    val cell: Int,
    val side: TangoEdgeSide,
    val kind: TangoMarkerKind,
)

/** `=` binds adjacent cells to the same value; `×` to opposite values. */
enum class TangoMarkerKind { SAME, OPPOSITE;

    companion object {
        fun fromByte(b: Int): TangoMarkerKind? = when (b) {
            0 -> SAME
            1 -> OPPOSITE
            else -> null
        }
    }

    fun toByte(): Int = ordinal
}

/**
 * A Tango puzzle: size-parameterized NxN board (A2, official daily 6×6), immutable givens
 * (A3), and `=`/`×` edge markers (A12). Row-major cell indices, `idx = row*n + col`.
 */
data class TangoPuzzle(
    val variant: VariantId,
    val n: Int,
    /** Immutable givens keyed by row-major cell index. */
    val givens: Map<Int, TangoCell>,
    /** Interior edge markers (A12: constraints, not cells). */
    val markers: List<TangoMarker>,
    /** Bytes a newer codec version appended (A19); re-emitted verbatim by TangoCodec. */
    val unknownTail: ByteArray = ByteArray(0),
) {
    val cellCount: Int = n * n

    init {
        require(n in MIN_N..MAX_N) { "board size $n out of range [$MIN_N..$MAX_N]" }
        require(givens.keys.all { it in 0 until cellCount }) { "given index out of range" }
        require(markers.all { it.isValidFor(n) }) { "marker edge out of range" }
    }

    fun isGiven(idx: Int): Boolean = idx in givens

    /** The `=`/`×` relation this puzzle pins between 4-adjacent [a] and [b], or null. */
    fun markerBetween(a: Int, b: Int): TangoMarkerKind? {
        val (cell, side) = when {
            b - a == 1 && a % n < n - 1 -> a to TangoEdgeSide.RIGHT
            a - b == 1 && b % n < n - 1 -> b to TangoEdgeSide.RIGHT
            b - a == n -> a to TangoEdgeSide.DOWN
            a - b == n -> b to TangoEdgeSide.DOWN
            else -> return null
        }
        return markers
            .firstOrNull { it.cell == cell && it.side == side }
            ?.kind
    }

    private fun TangoMarker.isValidFor(n: Int): Boolean {
        if (cell !in 0 until n * n) return false
        return when (side) {
            TangoEdgeSide.RIGHT -> cell % n < n - 1
            TangoEdgeSide.DOWN -> cell / n < n - 1
        }
    }

    override fun equals(other: Any?): Boolean =
        other is TangoPuzzle &&
            variant == other.variant &&
            n == other.n &&
            givens == other.givens &&
            markers == other.markers &&
            unknownTail.contentEquals(other.unknownTail)

    override fun hashCode(): Int {
        var h = variant.hashCode()
        h = 31 * h + n
        h = 31 * h + givens.hashCode()
        h = 31 * h + markers.hashCode()
        h = 31 * h + unknownTail.contentHashCode()
        return h
    }

    companion object {
        const val MIN_N: Int = 2
        const val MAX_N: Int = 11 // u8 budget: givens/cells <= n^2 (121 at n=11), like Zip
    }
}

/** Official daily identity (zip precedent): the LinkedIn day plus the puzzle number, null for custom boards. */
data class TangoPuzzleRef(
    override val date: java.time.LocalDate,
    override val puzzleNumber: Int?,
) : PuzzleRef {
    override val gameId: GameId get() = TangoDefinition.id
}
