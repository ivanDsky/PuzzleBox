package com.ivandsky.puzzlebox.games.queens.editor

import com.ivandsky.puzzlebox.game.editor.PublishBlock
import com.ivandsky.puzzlebox.game.editor.Verdict

/**
 * Queens board gates outside the solver (spec A8). Contiguity is a gate, not a verdict: a
 * split region blocks publishing whatever [Verdict] the solver gave, one block per region
 * naming its pieces' count and every cell. A Unique board the hint ladder cannot finish
 * ([Verdict.Unique.needsGuessing], spec A9) adds one [PublishBlock.NEEDS_GUESSING], after the splits.
 */
object QueensPublishGates {
    /** Every PublishBlock id blocks can emit (QueensCopyCoverageTest, 25). */
    val blockIds: Set<String> = setOf(PublishBlock.SPLIT_REGION, PublishBlock.NEEDS_GUESSING)

    fun blocks(draft: QueensDraft, verdict: Verdict): List<PublishBlock> {
        val splits = QueensContiguity.splitRegions(draft).map { (region, pieces) ->
            PublishBlock(
                id = PublishBlock.SPLIT_REGION,
                subject = region,
                count = pieces.size,
                cells = pieces.flatMapTo(sortedSetOf()) { it },
            )
        }
        val guessing = verdict is Verdict.Unique && verdict.needsGuessing
        return if (guessing) splits + PublishBlock(PublishBlock.NEEDS_GUESSING) else splits
    }
}
