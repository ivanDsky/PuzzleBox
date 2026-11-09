package com.ivandsky.puzzlebox.create

import androidx.compose.foundation.clickable
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.ivandsky.puzzlebox.game.DecodeError
import com.ivandsky.puzzlebox.game.DecodeResult
import com.ivandsky.puzzlebox.game.VariantId
import com.ivandsky.puzzlebox.game.editor.Budget
import com.ivandsky.puzzlebox.game.editor.DraftState
import com.ivandsky.puzzlebox.game.editor.EditorDraft
import com.ivandsky.puzzlebox.game.editor.GameEditor
import com.ivandsky.puzzlebox.game.editor.IncompleteReason
import com.ivandsky.puzzlebox.game.editor.PublishBlock
import com.ivandsky.puzzlebox.game.editor.Solution
import com.ivandsky.puzzlebox.game.editor.Verdict
import com.ivandsky.puzzlebox.gameui.PlayLayout
import com.ivandsky.puzzlebox.gameui.editor.EditorOverlay
import com.ivandsky.puzzlebox.gameui.editor.GameEditorController
import com.ivandsky.puzzlebox.gameui.editor.GameEditorUi
import com.ivandsky.puzzlebox.model.GameId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The stand-in game for feature:create:impl tests (ADR-0019 Consequences: the editor seam's
 * second adapter). A board is a mark count; its draft codec is `[version, marks]`, so stored
 * rows stay readable in assertions. By default a board with marks is Unique with the mark
 * count as its solution and a blank board is Incomplete; tests swap [analysis] for slow,
 * never-ending or reentrant solves.
 */
data class TestDraft(val marks: Int = 0)

class FakeGameEditor(
    override val gameId: GameId = GameId("fake"),
    override val variants: List<VariantId> = listOf(VariantId("${gameId.value}/v1")),
) : GameEditor<TestDraft> {

    override val draftFormatVersion: Int = 1

    /** Every board [analyze] was asked about, in call order. */
    val analyzed = mutableListOf<TestDraft>()

    var analysis: (TestDraft, Budget) -> Verdict = { draft, _ -> defaultVerdict(draft) }

    override fun newDraft(variant: VariantId): TestDraft = TestDraft()

    override fun variantOf(draft: TestDraft): VariantId = variants.first()

    override fun encodeDraft(state: DraftState<TestDraft>): ByteArray =
        byteArrayOf(draftFormatVersion.toByte(), state.draft.marks.toByte())

    override fun decodeDraft(bytes: ByteArray): DecodeResult<DraftState<TestDraft>> = when {
        bytes.size != 2 -> DecodeResult.Err(DecodeError.Malformed("fake draft is 2 bytes"))
        bytes[0].toInt() != draftFormatVersion ->
            DecodeResult.Err(DecodeError.UnsupportedVersion(bytes[0].toInt(), draftFormatVersion))
        else -> DecodeResult.Ok(DraftState(TestDraft(bytes[1].toInt())))
    }

    override fun analyze(draft: TestDraft, budget: Budget): Verdict {
        analyzed += draft
        return analysis(draft, budget)
    }

    /** A handle on [marks] the way features hold drafts. */
    fun draft(marks: Int): EditorDraft<TestDraft> = EditorDraft(this, DraftState(TestDraft(marks)))

    companion object {
        fun defaultVerdict(draft: TestDraft): Verdict =
            if (draft.marks == 0) Verdict.Incomplete(IncompleteReason("fake.blank", 1))
            else Verdict.Unique(Solution(listOf(draft.marks)))
    }
}

/**
 * The Compose half: a board that shows the draft's mark count under `fake.editor.board`. With
 * [gameSlots] it also draws its pane tools (`fake.editor.paneTools`) and publish fields
 * (`fake.editor.publishFields`, `.disabled` while not enabled), and its board names the layout
 * it was drawn in (`fake.editor.board.<layout>`).
 */
class FakeGameEditorUi(
    override val gameId: GameId = GameId("fake"),
    private val gameSlots: Boolean = false,
) : GameEditorUi {

    val controllers = mutableListOf<FakeEditorController>()

    override fun sizeLabel(variant: VariantId): String = "1×1"

    override fun createController(initial: EditorDraft<*>, onEdit: (EditorDraft<*>) -> Unit): GameEditorController =
        FakeEditorController(initial, onEdit).also { controllers += it }

    @Composable
    override fun Board(controller: GameEditorController, verdict: Verdict, modifier: Modifier) {
        Text("${controller.draft.value.encode().last()}", modifier.testTag("fake.editor.board"))
    }

    @Composable
    override fun Board(controller: GameEditorController, verdict: Verdict, layout: PlayLayout, modifier: Modifier) {
        if (gameSlots) {
            Text("${controller.draft.value.encode().last()}", modifier.testTag("fake.editor.board.$layout"))
        } else {
            Board(controller, verdict, modifier)
        }
    }

    @Composable
    override fun PaneTools(controller: GameEditorController, modifier: Modifier) {
        if (gameSlots) Text("Pane tools", modifier.testTag("fake.editor.paneTools"))
    }

    @Composable
    override fun PublishFields(controller: GameEditorController, enabled: Boolean, modifier: Modifier) {
        if (gameSlots) Text("Game fields", modifier.testTag(if (enabled) "fake.editor.publishFields" else "fake.editor.publishFields.disabled"))
    }

    /** One fix row whose Select selects [FakeEditorController.FIX_CELLS], then calls [onSelect]. */
    @Composable
    override fun Issues(
        controller: GameEditorController,
        verdict: Verdict,
        blocks: List<PublishBlock>,
        onSelect: () -> Unit,
        modifier: Modifier,
    ) {
        Text(
            text = "Select",
            modifier = modifier.testTag("fake.editor.fix.select").clickable {
                (controller as FakeEditorController).selected = FakeEditorController.FIX_CELLS
                onSelect()
            },
        )
    }
}

/** Holds the draft; [edit] is the author's gesture, [adopted] the VM's replacements. */
class FakeEditorController(
    initial: EditorDraft<*>,
    private val onEdit: (EditorDraft<*>) -> Unit,
) : GameEditorController {

    private val current = MutableStateFlow(initial)
    override val draft: StateFlow<EditorDraft<*>> = current.asStateFlow()

    private val shownOverlay = MutableStateFlow(EditorOverlay())
    override val overlay: StateFlow<EditorOverlay> = shownOverlay.asStateFlow()

    val adopted = mutableListOf<EditorDraft<*>>()

    /** What the fake fix row's Select picked. */
    var selected: Set<Int> = emptySet()

    override fun adopt(draft: EditorDraft<*>) {
        adopted += draft
        current.value = draft
    }

    override fun adoptOverlay(overlay: EditorOverlay) {
        shownOverlay.value = overlay
    }

    fun edit(next: EditorDraft<*>) {
        current.value = next
        onEdit(next)
    }

    companion object {
        val FIX_CELLS: Set<Int> = setOf(0, 2)
    }
}
