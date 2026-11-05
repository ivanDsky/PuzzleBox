package com.ivandsky.puzzlebox.games.strandsui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxPreviews
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.games.strands.ActiveHint
import com.ivandsky.puzzlebox.games.strands.Coord
import com.ivandsky.puzzlebox.games.strands.HintStage
import com.ivandsky.puzzlebox.games.strands.StrandsPuzzle
import com.ivandsky.puzzlebox.games.strands.StrandsState
import com.ivandsky.puzzlebox.games.strands.StrandsWord
import com.ivandsky.puzzlebox.games.strands.render.StrandsRendererModel

/**
 * One pane per board and chrome state, light and dark (@PuzzleBoxPreviews), built from real
 * [StrandsState] values through the same [StrandsRendererModel] the play screen draws.
 * The board is a 4×4: PIES, BAKE (the spangram, edge to edge), TART, CAKE.
 */
private val previewPuzzle: StrandsPuzzle by lazy {
    val rows = listOf("PIES", "BAKE", "TART", "CAKE")
    val words = rows.mapIndexed { row, text -> StrandsWord(text, text.indices.map { Coord(row, it) }) }
    StrandsPuzzle(
        rows = 4,
        cols = 4,
        letters = rows.joinToString(""),
        clue = "Bake sale",
        spangram = words[1],
        themeWords = words - words[1],
        validWords = rows.toSet(),
        constructors = "PuzzleBox",
    )
}

private fun model(state: StrandsState = StrandsState(previewPuzzle), trace: List<Coord> = emptyList()) =
    StrandsRendererModel.of(state, trace)

@Composable
private fun BoardPane(model: StrandsRendererModel) {
    PuzzleBoxTheme {
        StrandsBoardContent(model = model, onTap = {}, modifier = Modifier.fillMaxWidth())
    }
}

@PuzzleBoxPreviews
@Preview(name = "Compact width", widthDp = 360, heightDp = 400, showBackground = true)
@Composable
internal fun StrandsBoardIdlePreview() = BoardPane(model())

@PuzzleBoxPreviews
@Preview(name = "Compact width", widthDp = 360, heightDp = 400, showBackground = true)
@Composable
internal fun StrandsBoardTracingPreview() =
    BoardPane(model(trace = listOf(Coord(2, 0), Coord(2, 1), Coord(3, 2))))

@PuzzleBoxPreviews
@Preview(name = "Compact width", widthDp = 360, heightDp = 400, showBackground = true)
@Composable
internal fun StrandsBoardFoundPreview() =
    BoardPane(model(StrandsState(previewPuzzle, found = listOf("PIES", "BAKE"))))

@PuzzleBoxPreviews
@Preview(name = "Compact width", widthDp = 360, heightDp = 400, showBackground = true)
@Composable
internal fun StrandsBoardHintOutlinedPreview() =
    BoardPane(model(StrandsState(previewPuzzle, hintsUsed = 1, activeHint = ActiveHint("TART", HintStage.Outlined))))

@PuzzleBoxPreviews
@Preview(name = "Compact width", widthDp = 360, heightDp = 400, showBackground = true)
@Composable
internal fun StrandsBoardHintOrderPreview() =
    BoardPane(model(StrandsState(previewPuzzle, hintsUsed = 2, activeHint = ActiveHint("TART", HintStage.OrderRevealed))))

@PuzzleBoxPreviews
@Preview(name = "Compact width", widthDp = 360, heightDp = 400, showBackground = true)
@Composable
internal fun StrandsBoardSolvedPreview() =
    BoardPane(model(StrandsState(previewPuzzle, found = listOf("PIES", "BAKE", "TART", "CAKE"))))

@PuzzleBoxPreviews
@Preview(name = "Compact width", widthDp = 360, heightDp = 240, showBackground = true)
@Composable
internal fun StrandsChromePreview() {
    PuzzleBoxTheme {
        Column {
            StrandsClue(StrandsHeader(clue = "Bake sale", puzzleNumber = 669, credit = StrandsCopy.credit("PuzzleBox")))
            StrandsSlotLine(WordSlot.Verdict(StrandsCopy.SPANGRAM, VerdictKind.Spangram, seq = 1))
            StrandsControls(found = 2, total = 4, hint = HintUi(available = true, meterThirds = 1), onHint = {})
        }
    }
}

@PuzzleBoxPreviews
@Preview(name = "Compact width", widthDp = 360, heightDp = 240, showBackground = true)
@Composable
internal fun StrandsChromeRejectedPreview() {
    PuzzleBoxTheme {
        Column {
            StrandsSlotLine(WordSlot.Verdict(StrandsCopy.NOT_IN_WORD_LIST, VerdictKind.Reject, seq = 1))
            StrandsControls(found = 0, total = 4, hint = HintUi(available = false, meterThirds = 2), onHint = {})
        }
    }
}
