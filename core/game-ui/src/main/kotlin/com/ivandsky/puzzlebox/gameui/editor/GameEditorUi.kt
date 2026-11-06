package com.ivandsky.puzzlebox.gameui.editor

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.ivandsky.puzzlebox.game.VariantId
import com.ivandsky.puzzlebox.game.editor.EditorDraft
import com.ivandsky.puzzlebox.game.editor.PublishBlock
import com.ivandsky.puzzlebox.game.editor.Verdict
import com.ivandsky.puzzlebox.model.GameId

/**
 * One game's editor UI (ADR-0019): the Compose half of
 * [com.ivandsky.puzzlebox.game.editor.GameEditor]. The create feature draws any game's
 * Studio through this seam and never names a game. [Board]'s signature is fixed for M2–M5:
 * the game draws its own diagnostics from [Verdict].
 */
interface GameEditorUi {
    val gameId: GameId

    /** "7×7" etc., used by the title line, size popover and draft rows. */
    fun sizeLabel(variant: VariantId): String

    /** UI-only state (selection, ring, overlay) lives in the controller; the VM owns the draft. */
    fun createController(initial: EditorDraft<*>, onEdit: (EditorDraft<*>) -> Unit): GameEditorController

    @Composable
    fun Board(controller: GameEditorController, verdict: Verdict, modifier: Modifier)

    /** Context line beside the tool strip; the default draws nothing. */
    @Composable fun ToolHint(controller: GameEditorController, modifier: Modifier) {}

    /** Fix list inside the verdict card; the default draws nothing. */
    @Composable fun Issues(
        controller: GameEditorController,
        verdict: Verdict,
        blocks: List<PublishBlock>,
        modifier: Modifier,
    ) {}
}
