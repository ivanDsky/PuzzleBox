package com.ivandsky.puzzlebox.play

import com.ivandsky.puzzlebox.game.DecodeError
import com.ivandsky.puzzlebox.game.DecodeResult
import com.ivandsky.puzzlebox.game.GamePlugin
import com.ivandsky.puzzlebox.game.GameRegistry
import com.ivandsky.puzzlebox.game.Puzzle
import com.ivandsky.puzzlebox.game.VariantId
import com.ivandsky.puzzlebox.game.editor.Budget
import com.ivandsky.puzzlebox.game.editor.DraftExport
import com.ivandsky.puzzlebox.game.editor.DraftState
import com.ivandsky.puzzlebox.game.editor.EditorShareCodec
import com.ivandsky.puzzlebox.game.editor.GameEditor
import com.ivandsky.puzzlebox.game.editor.Solution
import com.ivandsky.puzzlebox.game.editor.Verdict
import com.ivandsky.puzzlebox.testing.game.TestGames

/**
 * A share codec for [TestGames.alpha]: one cell, shared as `[MAGIC, cell]`. Every cell has one
 * solution, and the verified board exports as `TestGames.puzzle(alpha, cell)`, which alpha plays again.
 */
internal object AlphaShareEditor : GameEditor<Byte>, EditorShareCodec<Byte>, DraftExport<Byte> {
    const val SHARE_VERSION = 1

    override val gameId = TestGames.alpha
    override val variants = listOf(TestGames.variant(TestGames.alpha))
    override val draftFormatVersion = 1
    override val shareFormatVersion = SHARE_VERSION

    override fun newDraft(variant: VariantId): Byte = 0
    override fun variantOf(draft: Byte): VariantId = variants.single()
    override fun encodeDraft(state: DraftState<Byte>): ByteArray = encodeShare(state.draft)
    override fun decodeDraft(bytes: ByteArray): DecodeResult<DraftState<Byte>> = when (val cell = decodeShare(bytes)) {
        is DecodeResult.Ok -> DecodeResult.Ok(DraftState(cell.value))
        is DecodeResult.Err -> cell
    }

    override fun analyze(draft: Byte, budget: Budget): Verdict = Verdict.Unique(Solution(listOf(draft.toInt())))

    override fun encodeShare(draft: Byte): ByteArray = byteArrayOf(TestGames.MAGIC, draft)
    override fun decodeShare(bytes: ByteArray): DecodeResult<Byte> =
        if (bytes.size == 2 && bytes[0] == TestGames.MAGIC) DecodeResult.Ok(bytes[1])
        else DecodeResult.Err(DecodeError.Malformed("not an alpha share"))

    override fun toPuzzle(draft: Byte, solution: Solution): Puzzle = TestGames.puzzle(TestGames.alpha, draft)

    /** A registry of alpha alone, with this editor, so a shared alpha code verifies. */
    fun registry(): GameRegistry {
        val base = TestGames.plugin(TestGames.alpha)
        return GameRegistry(
            listOf(
                GamePlugin(
                    definition = base.definition,
                    rules = base.rules,
                    session = base.session,
                    score = base.score,
                    stateOf = base.stateOf,
                    initialSession = base.initialSession,
                    refOf = base.refOf,
                    playAgainSession = base.playAgainSession,
                    rendererModel = base.rendererModel,
                    editor = this,
                ),
            ),
        )
    }
}
