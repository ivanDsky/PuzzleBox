package com.ivandsky.puzzlebox.games.strands.editor

import com.ivandsky.puzzlebox.game.editor.PublishBlock
import com.ivandsky.puzzlebox.game.editor.Verdict
import com.ivandsky.puzzlebox.games.strands.feed.gate.SpangramEdgesCheck

/**
 * Strands gates outside the solver (spec A12): the clue length, the spangram's edges, each word's
 * shape, blocklisted words and repeated words. Word blocks name the path index; unnamed paths
 * raise none, the verdict already reports them. Blocks come in that order, each by ascending path
 * index.
 */
object StrandsPublishGates {
    const val CLUE_LENGTH: String = "strands.clue-length"
    const val WORD_SHAPE: String = "strands.word-shape"
    const val DUPLICATE_WORD: String = "strands.duplicate-word"
    const val SPANGRAM_EDGES: String = "strands.spangram-edges"

    /** Every PublishBlock id [blocks] can emit, so a copy table can prove it covers them all. */
    val blockIds: Set<String> = setOf(PublishBlock.BLOCKLISTED, CLUE_LENGTH, WORD_SHAPE, DUPLICATE_WORD, SPANGRAM_EDGES)

    private const val MAX_CLUE = 40
    private val WORD_LENGTHS = 4..24

    fun blocks(draft: StrandsDraft, verdict: Verdict): List<PublishBlock> {
        val words = draft.paths.mapIndexedNotNull { i, path -> path.word?.let { i to it } }
        val blocks = mutableListOf<PublishBlock>()
        if (draft.clue.isBlank() || draft.clue.length > MAX_CLUE) blocks += PublishBlock(CLUE_LENGTH)
        draft.paths.getOrNull(draft.spangram)?.let { path ->
            val coords = path.cells.map { listOf(it / StrandsDraft.COLS, it % StrandsDraft.COLS) }
            if (!SpangramEdgesCheck.spans(coords, StrandsDraft.ROWS, StrandsDraft.COLS)) blocks += PublishBlock(SPANGRAM_EDGES)
        }
        words.filter { (_, w) -> !isShaped(w) }.mapTo(blocks) { (i, _) -> PublishBlock(WORD_SHAPE, subject = i) }
        words.filter { (_, w) -> StrandsDictionary.isBlocklisted(w.uppercase()) }
            .mapTo(blocks) { (i, _) -> PublishBlock(PublishBlock.BLOCKLISTED, subject = i) }
        val seen = mutableSetOf<String>()
        words.filter { (_, w) -> !seen.add(w.uppercase()) }
            .mapTo(blocks) { (i, _) -> PublishBlock(DUPLICATE_WORD, subject = i) }
        return blocks
    }

    private fun isShaped(word: String): Boolean = word.length in WORD_LENGTHS && word.all { it in 'A'..'Z' }
}
