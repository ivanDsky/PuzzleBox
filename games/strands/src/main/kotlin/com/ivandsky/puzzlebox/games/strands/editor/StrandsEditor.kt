package com.ivandsky.puzzlebox.games.strands.editor

import com.ivandsky.puzzlebox.game.DecodeResult
import com.ivandsky.puzzlebox.game.Puzzle
import com.ivandsky.puzzlebox.game.VariantId
import com.ivandsky.puzzlebox.game.editor.Budget
import com.ivandsky.puzzlebox.game.editor.DraftExport
import com.ivandsky.puzzlebox.game.editor.DraftState
import com.ivandsky.puzzlebox.game.editor.EditorNotation
import com.ivandsky.puzzlebox.game.editor.EditorShareCodec
import com.ivandsky.puzzlebox.game.editor.GameEditor
import com.ivandsky.puzzlebox.game.editor.NotationResult
import com.ivandsky.puzzlebox.game.editor.PublishBlock
import com.ivandsky.puzzlebox.game.editor.Solution
import com.ivandsky.puzzlebox.game.editor.Verdict
import com.ivandsky.puzzlebox.games.strands.Coord
import com.ivandsky.puzzlebox.games.strands.StrandsDefinition
import com.ivandsky.puzzlebox.games.strands.StrandsPuzzle
import com.ivandsky.puzzlebox.games.strands.StrandsWord
import com.ivandsky.puzzlebox.games.strands.codec.StrandsCodec
import com.ivandsky.puzzlebox.model.GameId

/**
 * Strands' pure editor (ADR-0019): one variant, a new board has blank letters, an empty clue and
 * no paths; drafts persist through [StrandsDraftCodec], analysis is [StrandsDraftAnalyzer] and a
 * draft shares as paths-free [StrandsShareCodec] bytes whose tiling the recipient re-derives.
 * [toPuzzle] bakes [StrandsValidWords] in, so it loads the dictionary: call it off the main thread.
 * The clue is a publish detail, written after the test solve, so it is no part of the layout
 * ([layoutOf]) and editing it keeps the test.
 */
object StrandsEditor :
    GameEditor<StrandsDraft>,
    DraftExport<StrandsDraft>,
    EditorNotation<StrandsDraft>,
    EditorShareCodec<StrandsDraft> by StrandsShareCodec {
    override val gameId: GameId get() = StrandsDefinition.id

    override val variants: List<VariantId> = listOf(StrandsDefinition.VARIANT)

    override val draftFormatVersion: Int get() = StrandsDraftCodec.VERSION

    override fun newDraft(variant: VariantId): StrandsDraft {
        require(variant == StrandsDefinition.VARIANT) { "unknown Strands variant $variant" }
        return StrandsDraft(StrandsDraft.BLANK.toString().repeat(StrandsDraft.ROWS * StrandsDraft.COLS), "", emptyList(), StrandsDraft.NO_SPANGRAM)
    }

    override fun variantOf(draft: StrandsDraft): VariantId = StrandsDefinition.VARIANT

    override fun encodeDraft(state: DraftState<StrandsDraft>): ByteArray = StrandsDraftCodec.encode(state)

    override fun decodeDraft(bytes: ByteArray): DecodeResult<DraftState<StrandsDraft>> = StrandsDraftCodec.decode(bytes)

    override val notationVersion: Int get() = StrandsNotation.VERSION

    override fun parse(text: String): NotationResult<StrandsDraft> = StrandsNotation.parse(text)

    override fun print(draft: StrandsDraft): String = StrandsNotation.print(draft)

    override fun columns(draft: StrandsDraft): Int = StrandsDraft.COLS

    override fun analyze(draft: StrandsDraft, budget: Budget): Verdict = StrandsDraftAnalyzer.analyze(draft, budget)

    override fun publishBlocks(draft: StrandsDraft, verdict: Verdict): List<PublishBlock> =
        StrandsPublishGates.blocks(draft, verdict)

    override fun layoutOf(draft: StrandsDraft): StrandsDraft = draft.copy(clue = "")

    /**
     * StrandsCodec bytes of the draft with the words [solution] tiles it into, in drawn order;
     * [StrandsPuzzle.validWords] are the board's dictionary words plus those words.
     */
    override fun toPuzzle(draft: StrandsDraft, solution: Solution): Puzzle {
        val words = draft.paths.mapIndexed { index, path ->
            val text = checkNotNull(path.word).uppercase()
            val cells = solution.values.indices.filter { solution.values[it] == index }
            StrandsWord(text, spelled(draft, path, text, cells).map { Coord(it / StrandsDraft.COLS, it % StrandsDraft.COLS) })
        }
        val puzzle = StrandsPuzzle(
            rows = StrandsDraft.ROWS,
            cols = StrandsDraft.COLS,
            letters = draft.letters,
            clue = draft.clue,
            spangram = words[draft.spangram],
            themeWords = words.filterIndexed { index, _ -> index != draft.spangram },
            validWords = StrandsValidWords.of(draft.letters, words.mapTo(HashSet()) { it.text }),
            constructors = "",
        )
        return StrandsCodec.envelope(puzzle)
    }

    /** The drawn order when the path still covers exactly [cells], else any spelling of [text] over them. */
    private fun spelled(draft: StrandsDraft, path: StrandsDraftPath, text: String, cells: List<Int>): List<Int> {
        if (path.cells.toSet() == cells.toSet()) return path.cells
        val left = cells.toMutableSet()
        val order = ArrayList<Int>(cells.size)
        fun extend(): Boolean {
            if (order.size == text.length) return true
            val last = order.lastOrNull()
            for (cell in left.toList()) {
                val adjacent = last == null || Coord(last / StrandsDraft.COLS, last % StrandsDraft.COLS)
                    .isAdjacentTo(Coord(cell / StrandsDraft.COLS, cell % StrandsDraft.COLS))
                if (draft.letters[cell] != text[order.size] || !adjacent) continue
                left.remove(cell)
                order.add(cell)
                if (extend()) return true
                order.removeAt(order.lastIndex)
                left.add(cell)
            }
            return false
        }
        check(extend()) { "no path spells $text over its tiled cells" }
        return order
    }
}
