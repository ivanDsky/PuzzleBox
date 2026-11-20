package com.ivandsky.puzzlebox.designsystem.recap

import kotlin.random.Random

/** The mosaic is a square of [MOSAIC_SIDE] cells a side. */
const val MOSAIC_SIDE = 5

/** The cells in the mosaic. */
const val MOSAIC_CELLS = MOSAIC_SIDE * MOSAIC_SIDE

/**
 * The mosaic of a recap period: [MOSAIC_CELLS] keys, each key's cells in proportion to its share of [shares]'s counts
 * (largest remainder, ties to the earlier key), laid out by a shuffle seeded by [seed], so the same period draws the
 * same mosaic every time. Empty when there is nothing to share. Keys with a zero count get no cell.
 */
fun <K> recapMosaic(shares: List<Pair<K, Int>>, seed: String): List<K> {
    val total = shares.sumOf { it.second.coerceAtLeast(0) }
    if (total == 0) return emptyList()
    val quotas = shares.map { it.second.coerceAtLeast(0) * MOSAIC_CELLS / total }.toMutableList()
    val byRemainder = shares.indices.sortedWith(
        compareByDescending<Int> { shares[it].second.coerceAtLeast(0) * MOSAIC_CELLS % total }.thenBy { it },
    )
    byRemainder.take(MOSAIC_CELLS - quotas.sum()).forEach { quotas[it]++ }
    val cells = shares.flatMapIndexed { i, (key, _) -> List(quotas[i]) { key } }
    return cells.shuffled(Random(seed.hashCode()))
}
