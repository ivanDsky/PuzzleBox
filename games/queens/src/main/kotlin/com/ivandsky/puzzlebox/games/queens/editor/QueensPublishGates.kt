package com.ivandsky.puzzlebox.games.queens.editor

import com.ivandsky.puzzlebox.game.editor.PublishBlock
import com.ivandsky.puzzlebox.game.editor.Verdict

/**
 * Queens board gates outside the solver (spec A8). Contiguity is a gate, not a verdict: a
 * split region blocks publishing whatever [Verdict] the solver gave, one block per region
 * naming its pieces' count and every cell. Needs-guessing lands in 11.
 */
object QueensPublishGates {
    /** Every PublishBlock id blocks can emit (QueensCopyCoverageTest, 25). 11 adds NEEDS_GUESSING. */
    val blockIds: Set<String> = setOf(PublishBlock.SPLIT_REGION)

    fun blocks(draft: QueensDraft, verdict: Verdict): List<PublishBlock> =
        QueensContiguity.splitRegions(draft).map { (region, pieces) ->
            PublishBlock(
                id = PublishBlock.SPLIT_REGION,
                subject = region,
                count = pieces.size,
                cells = pieces.flatMapTo(sortedSetOf()) { it },
            )
        }
}
