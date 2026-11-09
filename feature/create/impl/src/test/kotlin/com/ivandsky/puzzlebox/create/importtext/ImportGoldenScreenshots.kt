package com.ivandsky.puzzlebox.create.importtext

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.ivandsky.puzzlebox.create.CreateScreen
import com.ivandsky.puzzlebox.create.CreateUiState
import com.ivandsky.puzzlebox.create.GOLDEN_PHONE
import com.ivandsky.puzzlebox.create.GOLDEN_TABLET
import com.ivandsky.puzzlebox.create.PreviewGames
import com.ivandsky.puzzlebox.create.editor.ActionArea
import com.ivandsky.puzzlebox.create.editor.StudioScreen
import com.ivandsky.puzzlebox.create.editor.ToolStrip
import com.ivandsky.puzzlebox.create.importtext.ImportSheetUi.ClipboardNotation
import com.ivandsky.puzzlebox.create.settleAndCapture
import com.ivandsky.puzzlebox.create.showGolden
import com.ivandsky.puzzlebox.designsystem.AppShapes
import com.ivandsky.puzzlebox.domain.ImportResult
import com.ivandsky.puzzlebox.game.VariantId
import com.ivandsky.puzzlebox.game.editor.PublishBlock
import com.ivandsky.puzzlebox.game.editor.Solution
import com.ivandsky.puzzlebox.game.editor.Verdict
import com.ivandsky.puzzlebox.gameui.PlayLayout
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The Import from text feature's looks (spec S15, A23), phone and tablet, light and dark,
 * rendered from fake state: the sheet empty (Import off), with the clipboard banner for one
 * line and for several, with text filled in, checking (spinner row, read-only box), and each
 * failure kind (malformed with line and column, header, unknown game, unsupported version, too
 * large). The sheet is its own window, so the whole screen is captured, over the Create root
 * it opens from. Then the New puzzle sheet's Import row, the Studio's overflow with Import from
 * text, and the Studio of an imported draft with its Imported chip: all three as they ship once
 * `TextImport` is on. Goldens in `snapshots/import/`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = GOLDEN_PHONE)
class ImportGoldenScreenshotsPhone : ImportGoldenScreenshots(PlayLayout.SinglePane, "phone")

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = GOLDEN_TABLET)
class ImportGoldenScreenshotsTablet : ImportGoldenScreenshots(PlayLayout.TwoPane, "tablet")

abstract class ImportGoldenScreenshots(private val layout: PlayLayout, private val device: String) {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    @Test fun `empty light`() = sheet("empty", OPEN, dark = false)
    @Test fun `empty dark`() = sheet("empty", OPEN, dark = true)

    @Test fun `clipboard one line light`() = sheet("clipboard-1-line", OPEN.copy(clipboard = CLIP_1), dark = false)
    @Test fun `clipboard one line dark`() = sheet("clipboard-1-line", OPEN.copy(clipboard = CLIP_1), dark = true)

    @Test fun `clipboard lines light`() = sheet("clipboard-lines", OPEN.copy(clipboard = CLIP_6), dark = false)
    @Test fun `clipboard lines dark`() = sheet("clipboard-lines", OPEN.copy(clipboard = CLIP_6), dark = true)

    @Test fun `filled light`() = sheet("filled", FILLED, dark = false)
    @Test fun `filled dark`() = sheet("filled", FILLED, dark = true)

    @Test fun `checking light`() = sheet("checking", FILLED.copy(checking = true, clipboard = CLIP_6), dark = false)
    @Test fun `checking dark`() = sheet("checking", FILLED.copy(checking = true, clipboard = CLIP_6), dark = true)

    @Test fun `malformed light`() = sheet("malformed", FILLED.copy(error = MALFORMED), dark = false)
    @Test fun `malformed dark`() = sheet("malformed", FILLED.copy(error = MALFORMED), dark = true)

    @Test fun `header light`() = sheet("header", FILLED.copy(error = HEADER), dark = false)
    @Test fun `header dark`() = sheet("header", FILLED.copy(error = HEADER), dark = true)

    @Test fun `unknown game light`() = sheet("unknown-game", FILLED.copy(error = UNKNOWN_GAME), dark = false)
    @Test fun `unknown game dark`() = sheet("unknown-game", FILLED.copy(error = UNKNOWN_GAME), dark = true)

    @Test fun `unsupported version light`() = sheet("unsupported-version", FILLED.copy(error = VERSION), dark = false)
    @Test fun `unsupported version dark`() = sheet("unsupported-version", FILLED.copy(error = VERSION), dark = true)

    @Test fun `too large light`() = sheet("too-large", FILLED.copy(error = TOO_LARGE), dark = false)
    @Test fun `too large dark`() = sheet("too-large", FILLED.copy(error = TOO_LARGE), dark = true)

    @Test fun `new puzzle import row light`() = newPuzzle(dark = false)
    @Test fun `new puzzle import row dark`() = newPuzzle(dark = true)

    @Test fun `studio overflow light`() = studio("studio-overflow", dark = false, overflow = true)
    @Test fun `studio overflow dark`() = studio("studio-overflow", dark = true, overflow = true)

    @Test fun `studio imported chip light`() = studio("studio-imported", dark = false, overflow = false)
    @Test fun `studio imported chip dark`() = studio("studio-imported", dark = true, overflow = false)

    private fun sheet(name: String, ui: ImportSheetUi, dark: Boolean) {
        rule.showGolden(dark) { CreateRoot(ui) }
        rule.settleAndCapture("import", name, device, dark)
    }

    private fun newPuzzle(dark: Boolean) {
        rule.showGolden(dark, frozen = false) { CreateRoot(ImportSheetUi()) }
        open(tap = "create.new", shows = "create.new.sheet")
        rule.settleAndCapture("import", "new-puzzle-row", device, dark)
    }

    private fun studio(name: String, dark: Boolean, overflow: Boolean) {
        rule.showGolden(dark, frozen = !overflow) { Studio() }
        if (overflow) open(tap = "create.studio.more", shows = "create.studio.import")
        rule.settleAndCapture("import", name, device, dark)
    }

    /** Taps [tap] and waits, on the running clock, until the window it opens shows [shows]. */
    private fun open(tap: String, shows: String) {
        rule.onNodeWithTag(tap).performClick()
        rule.waitUntil { rule.onAllNodesWithTag(shows).fetchSemanticsNodes().isNotEmpty() }
    }

    /** The Create root with a few drafts, the Import pill on, as the sheet opens over it. */
    @Composable
    private fun CreateRoot(ui: ImportSheetUi) {
        CreateScreen(
            state = ROOT,
            onNew = { _, _ -> },
            onOpen = {},
            onQuery = {},
            onFilter = {},
            onTab = {},
            onOpenPublished = {},
            onDuplicate = {},
            onDelete = {},
            onUndoDelete = {},
            importEnabled = true,
            importSheet = ui,
        )
    }

    /** An imported draft's Studio, its overflow offering Import from text. */
    @Composable
    private fun Studio() {
        StudioScreen(
            title = "Untitled 3",
            subtitle = "Queens 6×6",
            saving = false,
            verdict = Verdict.Unique(Solution(listOf(1, 9, 12, 22, 26, 35))),
            layout = layout,
            onBack = {},
            onRename = {},
            onDuplicate = {},
            onDelete = {},
            board = { boardModifier ->
                Box(boardModifier, contentAlignment = Alignment.Center) {
                    Box(
                        Modifier
                            .aspectRatio(1f, matchHeightConstraintsFirst = true)
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest, AppShapes.small),
                    )
                }
            },
            toolStrip = { stripModifier ->
                ToolStrip(
                    canUndo = true,
                    canRedo = false,
                    onUndo = {},
                    onRedo = {},
                    sizes = SIZES,
                    size = SIZES[2],
                    sizeLabel = { variant -> variant.value.substringAfter('/').let { "$it×$it" } },
                    onResize = {},
                    onClear = {},
                    hint = { Text("Drag across cells, then pick a colour", it, style = MaterialTheme.typography.bodySmall) },
                    modifier = stripModifier,
                )
            },
            actionArea = { areaModifier ->
                ActionArea(passed = null, canTestSolve = true, onTestSolve = {}, blocks = listOf(NOT_TESTED), modifier = areaModifier)
            },
            imported = true,
            onImport = {},
        )
    }

    private companion object {
        val SIZES = (4..8).map { VariantId("queens/$it") }
        val NOT_TESTED = PublishBlock(PublishBlock.NOT_TESTED)

        val OPEN = ImportSheetUi(visible = true)
        const val QUEENS = "puzzlebox queens v1\n00001\n00031\n42033\n43333\n43333\n"
        val FILLED = OPEN.copy(text = QUEENS)

        val CLIP_1 = ClipboardNotation("Queens", lines = 1, text = "puzzlebox queens v1\n")
        val CLIP_6 = ClipboardNotation("Queens", lines = 6, text = QUEENS)

        val MALFORMED = ImportResult.Malformed(line = 3, col = 2, message = "expected 5 rows")
        val HEADER = ImportResult.Malformed(line = 1, col = 1, message = "missing header")
        val UNKNOWN_GAME = ImportResult.UnknownGame("sudoku9")
        val VERSION = ImportResult.UnsupportedVersion(found = 9, maxKnown = 1)
        val TOO_LARGE = ImportResult.TooLarge(bytes = 66_000, line = 412, col = 1)

        val ROOT = CreateUiState(
            loading = false,
            drafts = (0 until 3).map { index ->
                val game = PreviewGames[index % PreviewGames.size]
                CreateUiState.DraftRow(
                    id = "d$index",
                    gameId = game.id,
                    brand = game.brand,
                    title = "Untitled ${3 - index}",
                    meta = "${game.name} · ${game.defaultSize.label}",
                    edited = "edited ${index + 1} h ago",
                    verdict = Verdict.Unique(Solution(listOf(1, 9, 12, 22, 26, 35))),
                    imported = index == 1,
                )
            },
            draftCount = 3,
            games = PreviewGames,
        )
    }
}
