package com.ivandsky.puzzlebox.games.queens

import com.ivandsky.puzzlebox.game.VariantId

/**
 * One rule regime as DATA (zip precedent, A18/ADR-0005). [size] is the board edge;
 * [queensPerUnit] is the A9 rule parameter — expanded variants (bigger boards, N≥2
 * queens) are NEW TABLE ENTRIES here, never new code paths (user ruling 2026-11-02).
 *
 * Table today: the official daily sizes (research 01/02 — dailies ship 8×8, 9×9, 10×10
 * with N=1). A9 expansion lands as additional entries verified by later tickets' tests.
 */
data class QueensVariant(
    val id: VariantId,
    val size: Int,
    val queensPerUnit: Int,
)

object QueensVariants {

    val table: Map<VariantId, QueensVariant> = listOf(8, 9, 10)
        .associate { size ->
            val id = queensVariantId(size)
            id to QueensVariant(id = id, size = size, queensPerUnit = 1)
        }

    val all: Collection<QueensVariant> get() = table.values

    /** Unknown ids are NEVER guessed (ADR-0005): null -> DecodeError.UnknownVariant. */
    fun byId(id: VariantId): QueensVariant? = table[id]
}
