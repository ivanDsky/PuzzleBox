package com.ivandsky.puzzlebox.games.queens

import com.ivandsky.puzzlebox.game.VariantId

/**
 * One rule regime as DATA (zip precedent, A18/ADR-0005). [size] is the board edge;
 * [queensPerUnit] is the A9 rule parameter — expanded variants (bigger boards, N≥2
 * queens) are NEW TABLE ENTRIES here, never new code paths (user ruling 2026-11-02).
 *
 * Board sizes (user ruling 2026-11-02, superseding research 01's pinned 8/9/10 claim —
 * the real daily edition 916 is 7×7): ANY size in 4..12 decodes. Queens rules are
 * size-independent (A9), the A13 uniqueness gate is the real safety net, and ADR-0005's
 * "never guess" applies to RULE variants, not board size. [DecodeError.UnknownVariant]
 * remains for sizes outside 4..12 and future rule-variant ids (e.g. 2-queens wire
 * formats). The id stays `queens/n<size>` for identity/telemetry.
 */
data class QueensVariant(
    val id: VariantId,
    val size: Int,
    val queensPerUnit: Int,
)

object QueensVariants {

    /** Bounded board-size range (user ruling 2026-11-02); anything else is UnknownVariant. */
    const val MIN_SIZE: Int = 4
    const val MAX_SIZE: Int = 12

    private val ID_PREFIX = "queens/n"

    fun variant(size: Int): QueensVariant =
        QueensVariant(id = queensVariantId(size), size = size, queensPerUnit = 1)

    val all: Collection<QueensVariant> get() = (MIN_SIZE..MAX_SIZE).map(::variant)

    /**
     * Unknown ids are NEVER guessed (ADR-0005): sizes outside 4..12 and non-size ids
     * return null -> DecodeError.UnknownVariant.
     */
    fun byId(id: VariantId): QueensVariant? =
        id.value.removePrefix(ID_PREFIX).toIntOrNull()
            ?.takeIf { it in MIN_SIZE..MAX_SIZE }
            ?.let(::variant)
}
