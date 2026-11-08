package com.ivandsky.puzzlebox.games.tango.editor

import com.ivandsky.puzzlebox.game.editor.PublishBlock
import com.ivandsky.puzzlebox.game.editor.Verdict

/**
 * Tango board gates outside the solver (spec A9). A Unique board the hint ladder cannot finish
 * ([Verdict.Unique.needsGuessing]) is blocked with one [PublishBlock.NEEDS_GUESSING].
 */
object TangoPublishGates {
    /** Every PublishBlock id [blocks] can emit, so a copy table can prove it covers them all. */
    val blockIds: Set<String> = setOf(PublishBlock.NEEDS_GUESSING)

    fun blocks(draft: TangoDraft, verdict: Verdict): List<PublishBlock> {
        val guessing = verdict is Verdict.Unique && verdict.needsGuessing
        return if (guessing) listOf(PublishBlock(PublishBlock.NEEDS_GUESSING)) else emptyList()
    }
}
