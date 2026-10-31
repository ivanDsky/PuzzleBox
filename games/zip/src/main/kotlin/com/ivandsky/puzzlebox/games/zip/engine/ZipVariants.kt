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
 * The variant table: LinkedIn's daily sizes n=5/6/7 (research 01). A new rule regime is a
 * new entry here — `:core:game` is never edited (F1 §2.1).
 */
object ZipVariants {

    val table: Map<VariantId, ZipVariant> = listOf(5, 6, 7)
        .associate { size ->
            val id = VariantId("zip/n$size")
            id to ZipVariant(id, size)
        }

    val all: Collection<ZipVariant> get() = table.values

    /** Unknown ids are NEVER guessed (ADR-0005): null -> DecodeError.UnknownVariant. */
    fun byId(id: VariantId): ZipVariant? = table[id]
}
