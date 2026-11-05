package com.ivandsky.puzzlebox.games.zip.engine

import com.ivandsky.puzzlebox.game.VariantId

/**
 * One rule regime as DATA (A18 / ADR-0005). [size] is the rules-boundary the engine reads
 * everywhere it would hardcode a bound; [features] shapes future rule checks — it ships
 * only with a real consumer and stays empty today (F2 §3, §2.1 anti-speculation rule).
 */
data class ZipVariant(
    val id: VariantId,
    val size: Int,
    val features: Set<Feature> = emptySet(),
)

/** Future special cells/mechanics. Empty until feedback demands one; adding one is a new variant. */
enum class Feature { BRIDGE, TELEPORT }

/**
 * The variant table: LinkedIn's daily sizes n=5..8 (research 01; ticket 19 amendment —
 * edition 594 shipped gridSize 8). A new rule regime is a new entry here — `:core:game`
 * is never edited (F1 §2.1). The tutorial's tiny boards (n=3, 4; spec A11/A18) are a
 * separate table: the codecs carry them, the daily feed never does.
 */
object ZipVariants {

    /** The daily sizes: the only regimes a daily (and so a leaderboard) can name. */
    val table: Map<VariantId, ZipVariant> = sized(5..8)

    /** Tutorial-only sizes (spec A11): hand-authored lessons, never a daily. */
    val tutorial: Map<VariantId, ZipVariant> = sized(3..4)

    /** The daily regimes; tutorial sizes are not in it. */
    val all: Collection<ZipVariant> get() = table.values

    /**
     * Any regime this build plays, daily or tutorial — the codecs' lookup. Unknown ids
     * are NEVER guessed (ADR-0005): null -> DecodeError.UnknownVariant.
     */
    fun byId(id: VariantId): ZipVariant? = table[id] ?: tutorial[id]

    /** Daily regimes only — the wire parser's gate, so a tutorial-sized daily never parses. */
    fun dailyById(id: VariantId): ZipVariant? = table[id]

    private fun sized(sizes: IntRange): Map<VariantId, ZipVariant> = sizes.associate { size ->
        val id = VariantId("zip/n$size")
        id to ZipVariant(id, size)
    }
}
