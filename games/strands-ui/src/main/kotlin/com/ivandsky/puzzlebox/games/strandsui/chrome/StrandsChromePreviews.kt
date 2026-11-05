package com.ivandsky.puzzlebox.games.strandsui.chrome

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxPreviews
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.games.strands.StrandsRating
import com.ivandsky.puzzlebox.games.strandsui.HintDialog
import com.ivandsky.puzzlebox.games.strandsui.HintUi
import com.ivandsky.puzzlebox.games.strandsui.StrandsCopy
import com.ivandsky.puzzlebox.games.strandsui.StrandsHeader
import com.ivandsky.puzzlebox.games.strandsui.VerdictKind
import com.ivandsky.puzzlebox.games.strandsui.WordSlot

/**
 * The chrome around the board, one pane per state, light and dark (@PuzzleBoxPreviews).
 * Motion is held still (reduce motion on) so each pane is the state's resting frame.
 */
private val header = StrandsHeader(clue = "Bake sale", puzzleNumber = 669, credit = StrandsCopy.credit("Tess Tester"))

@Composable
private fun ChromePane(content: @Composable () -> Unit) {
    PuzzleBoxTheme {
        Surface {
            Column(
                Modifier.padding(Spacing.lg),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) { content() }
        }
    }
}

@PuzzleBoxPreviews
@Preview(name = "Compact width", widthDp = 360, showBackground = true)
@Composable
internal fun StrandsChromeInPlayPreview() = ChromePane {
    ThemeCard(clue = header.clue, rating = null)
    StrandsHeaderLine(header)
    WordSlotLine(WordSlot.Tracing("TAR"), found = 1, total = 4, reduceMotion = true)
    FoundCounter(found = 1, total = 4)
}

@PuzzleBoxPreviews
@Composable
internal fun StrandsThemeCardEmojiCluePreview() = ChromePane {
    ThemeCard(clue = "Sweet treats \uD83C\uDF70", rating = null)
    StrandsHeaderLine(header.copy(puzzleNumber = null))
}

@PuzzleBoxPreviews
@Composable
internal fun StrandsThemeCardSolvedPreview() = ChromePane {
    for (rating in StrandsRating.entries) ThemeCard(clue = header.clue, rating = rating)
}

@PuzzleBoxPreviews
@Composable
internal fun StrandsWordSlotPreview() = ChromePane {
    WordSlotLine(WordSlot.Empty, found = 0, total = 4, reduceMotion = true)
    WordSlotLine(WordSlot.Tracing("PIE"), found = 0, total = 4, reduceMotion = true)
    WordSlotLine(WordSlot.Verdict("PIES", VerdictKind.Theme, seq = 1), found = 1, total = 4, reduceMotion = true)
    WordSlotLine(
        WordSlot.Verdict(StrandsCopy.SPANGRAM, VerdictKind.Spangram, seq = 2),
        found = 2,
        total = 4,
        reduceMotion = true,
    )
    WordSlotLine(WordSlot.Verdict("RAKE", VerdictKind.NonTheme, seq = 3), found = 2, total = 4, reduceMotion = true)
    WordSlotLine(
        WordSlot.Verdict(StrandsCopy.NOT_IN_WORD_LIST, VerdictKind.Reject, seq = 4),
        found = 2,
        total = 4,
        reduceMotion = true,
    )
}

@PuzzleBoxPreviews
@Composable
internal fun StrandsHintControlPreview() = ChromePane {
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        for (thirds in 0..2) {
            StrandsHintControl(HintUi(available = false, meterThirds = thirds), onHint = {}, reduceMotion = true)
        }
        StrandsHintControl(HintUi(available = true, meterThirds = 1), onHint = {}, reduceMotion = true)
    }
}

// AppDialog draws in its own window, which the static canvas skips; interactive mode shows it.
@PuzzleBoxPreviews
@Composable
internal fun StrandsHintModalOnBoardPreview() = ChromePane {
    HintModal(HintDialog.OnBoard, onDismiss = {})
}

@PuzzleBoxPreviews
@Composable
internal fun StrandsHintModalNoneAvailablePreview() = ChromePane {
    HintModal(HintDialog.NoneAvailable, onDismiss = {})
}
