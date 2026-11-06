package com.ivandsky.puzzlebox.domain

import com.ivandsky.puzzlebox.data.model.Draft
import com.ivandsky.puzzlebox.data.model.TestSolve
import com.ivandsky.puzzlebox.game.editor.EditorDraft
import com.ivandsky.puzzlebox.game.editor.PublishBlock
import com.ivandsky.puzzlebox.game.editor.Verdict

/**
 * Publish readiness (spec A8, A10, A25): everything that still keeps [draft] from publishing,
 * empty when it is ready. The game's board blocks come first, then the domain's own gates.
 * A test solve counts only for the layout it was solved on, so a title edit never voids it.
 */
class ValidatePublish {
    /** Board blocks + NOT_UNIQUE (anything but Unique) + title 1..60 + NOT_TESTED (testSolve?.layoutKey != draft.layoutKey). */
    operator fun invoke(draft: EditorDraft<*>, verdict: Verdict, title: String, testSolve: TestSolve?): List<PublishBlock> =
        buildList {
            addAll(draft.publishBlocks(verdict))
            if (verdict !is Verdict.Unique) add(PublishBlock(PublishBlock.NOT_UNIQUE))
            when {
                title.isBlank() -> add(PublishBlock(PublishBlock.EMPTY_TITLE))
                title.length > Draft.TITLE_MAX -> add(PublishBlock(PublishBlock.TITLE_TOO_LONG))
            }
            if (testSolve?.layoutKey != draft.layoutKey) add(PublishBlock(PublishBlock.NOT_TESTED))
        }

    companion object {
        /** Domain-owned block ids (ReadinessCopyTest, 33). */
        val blockIds: Set<String> = setOf(
            PublishBlock.NOT_UNIQUE, PublishBlock.EMPTY_TITLE, PublishBlock.TITLE_TOO_LONG, PublishBlock.NOT_TESTED,
        )
    }
}
