package com.ivandsky.puzzlebox.train.practice

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxPreviews
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.game.trainer.TechniqueId
import com.ivandsky.puzzlebox.gameui.trainer.PracticeStatus
import com.ivandsky.puzzlebox.gameui.trainer.TrainerTechnique

// The end of a board and of a chapter (prototype 03, variant A): the celebrated board at each point of its hold and the
// result page for each outcome, on a phone and a tablet. Reduced motion is the system setting, so it has no frame here.

private val LineLock = TrainerTechnique(TechniqueId("line-lock"), "Line lock", StandInLine)
private val Pointing = TrainerTechnique(TechniqueId("pointing-pair"), "Pointing pair (rows and columns)", "A pair in one line rules out the rest of it.")

private val Rows = listOf(
    BoardRow(1, BoardRowKind.Clean, 6),
    BoardRow(2, BoardRowKind.Clean, 5),
    BoardRow(3, BoardRowKind.Slip, 8),
    BoardRow(4, BoardRowKind.Shown, 1),
    BoardRow(5, BoardRowKind.Clean, 7),
)

private fun result(
    outcome: ChapterOutcome,
    best: Int,
    next: TrainerTechnique? = Pointing,
    allMastered: Boolean = false,
) = ChapterResult(
    technique = LineLock,
    next = next,
    outcome = outcome,
    boards = 5,
    boardsDone = 5,
    rights = 27,
    shown = 1,
    slips = 2,
    best = best,
    rows = Rows,
    allMastered = allMastered,
    techniques = 10,
)

private fun done() = practicing(PracticeStatus.Done(standInSpotlight()))

@PuzzleBoxPreviews
@Composable
private fun BoardFinishedPreview() =
    PracticePreviewFrame(done(), chapter = standInChapter(board = 2), hold = BoardHold(remainingMs = 2_400, waitsForTap = false))

@PuzzleBoxPreviews
@Composable
private fun BoardFinishedLastSecondPreview() =
    PracticePreviewFrame(done(), chapter = standInChapter(board = 2), hold = BoardHold(remainingMs = 300, waitsForTap = false))

@PuzzleBoxPreviews
@Composable
private fun LastBoardFinishedPreview() =
    PracticePreviewFrame(done(), chapter = standInChapter(board = 5), hold = BoardHold(remainingMs = 3_000, waitsForTap = false))

@PuzzleBoxPreviews
@Composable
private fun BoardFinishedWaitsForTapPreview() =
    PracticePreviewFrame(done(), chapter = standInChapter(board = 3), hold = BoardHold(remainingMs = 3_000, waitsForTap = true))

@PuzzleBoxPreviews
@Composable
private fun SinglesBoardFinishedPreview() =
    PracticePreviewFrame(done(), chapter = standInChapter(board = 4, boards = 4), hold = BoardHold(remainingMs = 2_000, waitsForTap = false))

@PuzzleBoxPreviews
@Composable
private fun BoardFinishedTabletPreview() = PuzzleBoxTheme {
    Box(Modifier.width(840.dp).height(700.dp)) {
        PracticePreviewFrame(done(), chapter = standInChapter(board = 2), hold = BoardHold(remainingMs = 2_000, waitsForTap = false), showBack = false)
    }
}

@Composable
private fun ResultFrame(result: ChapterResult, showBack: Boolean = true) = PuzzleBoxTheme {
    Surface(color = MaterialTheme.colorScheme.background) {
        ChapterResultPage(result, showBack, onNext = {}, onAgain = {}, onBack = {})
    }
}

@PuzzleBoxPreviews
@Composable
private fun ResultNewlyMasteredPreview() = ResultFrame(result(ChapterOutcome.NewlyMastered, best = 12))

@PuzzleBoxPreviews
@Composable
private fun ResultStillMasteredPreview() = ResultFrame(result(ChapterOutcome.StillMastered, best = 19))

@PuzzleBoxPreviews
@Composable
private fun ResultNotMasteredPreview() = ResultFrame(result(ChapterOutcome.NotMastered, best = 2))

@PuzzleBoxPreviews
@Composable
private fun ResultLastMasteredPreview() = ResultFrame(result(ChapterOutcome.NewlyMastered, best = 6, next = null, allMastered = true))

@PuzzleBoxPreviews
@Composable
private fun ResultLastMasteredSomeLeftPreview() = ResultFrame(result(ChapterOutcome.StillMastered, best = 6, next = null))

@PuzzleBoxPreviews
@Composable
private fun ResultLastNotMasteredPreview() = ResultFrame(result(ChapterOutcome.NotMastered, best = 3, next = null))

@PuzzleBoxPreviews
@Composable
private fun ResultTabletPreview() = PuzzleBoxTheme {
    Box(Modifier.width(900.dp).height(700.dp)) { ResultFrame(result(ChapterOutcome.NewlyMastered, best = 12), showBack = false) }
}

@PuzzleBoxPreviews
@Composable
private fun ResultNotMasteredTabletPreview() = PuzzleBoxTheme {
    Box(Modifier.width(900.dp).height(700.dp)) { ResultFrame(result(ChapterOutcome.NotMastered, best = 2), showBack = false) }
}
