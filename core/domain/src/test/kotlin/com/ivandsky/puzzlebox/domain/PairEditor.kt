package com.ivandsky.puzzlebox.domain

import com.ivandsky.puzzlebox.game.DecodeError
import com.ivandsky.puzzlebox.game.DecodeResult
import com.ivandsky.puzzlebox.game.GamePlugin
import com.ivandsky.puzzlebox.game.Puzzle
import com.ivandsky.puzzlebox.game.VariantId
import com.ivandsky.puzzlebox.game.editor.Budget
import com.ivandsky.puzzlebox.game.editor.DraftExport
import com.ivandsky.puzzlebox.game.editor.DraftState
import com.ivandsky.puzzlebox.game.editor.EditorShareCodec
import com.ivandsky.puzzlebox.game.editor.GameEditor
import com.ivandsky.puzzlebox.game.editor.Solution
import com.ivandsky.puzzlebox.game.editor.Verdict
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.testing.game.TestGames

/**
 * Two cells, each 1 or 2 and never equal; 0 is a blank. `[1, 0]` has one solution,
 * `[0, 0]` two. The share payload is the cells as bytes; the puzzle carries the solution.
 */
internal object PairEditor : GameEditor<List<Int>>, EditorShareCodec<List<Int>>, DraftExport<List<Int>> {
    val GAME = GameId("pair")
    const val SHARE_VERSION = 1
    val VARIANT = VariantId("pair/n2")

    override val gameId: GameId = GAME
    override val variants: List<VariantId> = listOf(VARIANT)
    override val draftFormatVersion: Int = 1
    override val shareFormatVersion: Int = SHARE_VERSION
    override fun newDraft(variant: VariantId): List<Int> = listOf(0, 0)
    override fun variantOf(draft: List<Int>): VariantId = VARIANT
    override fun encodeDraft(state: DraftState<List<Int>>): ByteArray = encodeShare(state.draft)
    override fun decodeDraft(bytes: ByteArray): DecodeResult<DraftState<List<Int>>> = when (val d = decodeShare(bytes)) {
        is DecodeResult.Ok -> DecodeResult.Ok(DraftState(d.value))
        is DecodeResult.Err -> d
    }

    override fun analyze(draft: List<Int>, budget: Budget): Verdict {
        val solutions = listOf(listOf(1, 2), listOf(2, 1))
            .filter { candidate -> draft.indices.all { draft[it] == 0 || draft[it] == candidate[it] } }
            .map(::Solution)
        return when (solutions.size) {
            0 -> Verdict.None(conflicts = draft.indices.toSet())
            1 -> Verdict.Unique(solutions.single())
            else -> Verdict.Multiple(solutions[0], solutions[1], differingCells = draft.indices.toSet())
        }
    }

    override fun encodeShare(draft: List<Int>): ByteArray = ByteArray(draft.size) { draft[it].toByte() }
    override fun decodeShare(bytes: ByteArray): DecodeResult<List<Int>> =
        if (bytes.size == 2 && bytes.all { it in 0..2 }) DecodeResult.Ok(bytes.map { it.toInt() })
        else DecodeResult.Err(DecodeError.Malformed("not a pair board"))

    override fun toPuzzle(draft: List<Int>, solution: Solution): Puzzle =
        Puzzle(gameId, VARIANT, 1, ByteArray(solution.values.size) { solution.values[it].toByte() })
}

internal fun withEditor(game: GameId, editor: GameEditor<*> = PairEditor): GamePlugin<*, *, *> {
    val base = TestGames.plugin(game)
    return GamePlugin(
        definition = base.definition,
        rules = base.rules,
        session = base.session,
        score = base.score,
        stateOf = base.stateOf,
        initialSession = base.initialSession,
        refOf = base.refOf,
        playAgainSession = base.playAgainSession,
        rendererModel = base.rendererModel,
        editor = editor,
    )
}
