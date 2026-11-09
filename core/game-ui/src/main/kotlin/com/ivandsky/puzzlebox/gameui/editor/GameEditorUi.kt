package com.ivandsky.puzzlebox.gameui.editor

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.ivandsky.puzzlebox.game.VariantId
import com.ivandsky.puzzlebox.game.editor.EditorDraft
import com.ivandsky.puzzlebox.game.editor.PublishBlock
import com.ivandsky.puzzlebox.game.editor.Verdict
import com.ivandsky.puzzlebox.gameui.PlayLayout
import com.ivandsky.puzzlebox.model.GameId

/**
 * One game's editor UI (ADR-0019): the Compose half of
 * [com.ivandsky.puzzlebox.game.editor.GameEditor]. The create feature draws any game's
 * Studio through this seam and never names a game. [Board] draws its own diagnostics from
 * [Verdict].
 */
interface GameEditorUi {
    val gameId: GameId

    /** "7×7" etc., used by the title line, size popover and draft rows. */
    fun sizeLabel(variant: VariantId): String

    /** UI-only state (selection, ring, overlay) lives in the controller; the VM owns the draft. */
    fun createController(initial: EditorDraft<*>, onEdit: (EditorDraft<*>) -> Unit): GameEditorController

    /**
     * The board in the host's [layout]. In [PlayLayout.TwoPane] the Studio draws [PaneTools]
     * in the side pane, so a game that has them leaves them off this board.
     */
    @Composable
    fun Board(controller: GameEditorController, verdict: Verdict, layout: PlayLayout, modifier: Modifier)

    /**
     * Authoring controls the tablet ([PlayLayout.TwoPane]) Studio draws in its side pane, under
     * the tool strip and away with it while the publish details show; the default draws nothing.
     */
    @Composable fun PaneTools(controller: GameEditorController, modifier: Modifier) {}

    /**
     * The game's own publish details, at the foot of the Studio's details form once the test
     * passes; disabled while not [enabled] (a publish runs). The default draws nothing.
     */
    @Composable fun PublishFields(controller: GameEditorController, enabled: Boolean, modifier: Modifier) {}

    /** Context line beside the tool strip; the default draws nothing. */
    @Composable fun ToolHint(controller: GameEditorController, modifier: Modifier) {}

    /**
     * Fix list inside the verdict card; the default draws nothing. A fix's Select calls
     * [onSelect] once its cells are selected, and the host closes the card.
     */
    @Composable fun Issues(
        controller: GameEditorController,
        verdict: Verdict,
        blocks: List<PublishBlock>,
        onSelect: () -> Unit,
        modifier: Modifier,
    ) {}
}
