package com.ivandsky.puzzlebox.games.queens.engine.hints.ladder

/**
 * A deduction a rung found, its [evidence] still unbuilt so [first] builds none for the losing
 * candidates. [rank] is the leading sort key for a rung that orders by band size (k) first; the rest
 * keep it 0 and order by the deduction alone.
 */
internal class Found<out D : QueensDeduction>(val deduction: D, val rank: Int = 0, val evidence: () -> Set<Int>) {
    fun proof(): QueensProof = QueensProof(deduction, evidence())
}

/** Every found deduction in [order], each with its evidence built as the sequence is read. */
internal fun <D : QueensDeduction> List<Found<D>>.proofs(order: Comparator<in Found<D>>): Sequence<QueensProof> =
    sortedWith(order).asSequence().map(Found<D>::proof)

/** The first found deduction in [order], or null; no evidence is built. */
internal fun <D : QueensDeduction> List<Found<D>>.first(order: Comparator<in Found<D>>): D? =
    minWithOrNull(order)?.deduction
