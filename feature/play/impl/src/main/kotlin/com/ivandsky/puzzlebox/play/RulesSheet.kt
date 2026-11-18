package com.ivandsky.puzzlebox.play

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.vector.ImageVector
import com.ivandsky.puzzlebox.designsystem.AccentSlot
import com.ivandsky.puzzlebox.designsystem.AppTextButton
import com.ivandsky.puzzlebox.designsystem.GameGlyphs
import com.ivandsky.puzzlebox.designsystem.PrimaryButton
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxPreviews
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.designsystem.colors
import com.ivandsky.puzzlebox.gameui.GameBrand
import com.ivandsky.puzzlebox.model.GameId
import kotlinx.coroutines.launch

/**
 * A game's rules over the live board: "How to play", the numbered [RulesSheetCopy.rules] on [brand]'s accent
 * discs, the controls lines, then Got it and Replay the tutorial. Both buttons slide the sheet down first and
 * then call [onDismiss] / [onReplayTutorial]; a swipe, a scrim tap or system Back calls [onDismiss] at once.
 * The body scrolls when the text is large.
 *
 * Motion: the sheet's **Slide in / Slide out** and the scrim's **Fade** are ModalBottomSheet's own.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RulesSheet(
    copy: RulesSheetCopy,
    brand: GameBrand,
    onDismiss: () -> Unit,
    onReplayTutorial: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val hideThen = { next: () -> Unit -> scope.launch { sheetState.hide() }.invokeOnCompletion { next() } }
    ModalBottomSheet(onDismissRequest = onDismiss, modifier = modifier.testTag("rules.sheet"), sheetState = sheetState) {
        RulesSheetBody(copy, brand, onGotIt = { hideThen(onDismiss) }, onReplay = { hideThen(onReplayTutorial) })
    }
}

@Composable
private fun RulesSheetBody(copy: RulesSheetCopy, brand: GameBrand, onGotIt: () -> Unit, onReplay: () -> Unit) {
    val accent = brand.accent.colors()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(start = Spacing.lg, end = Spacing.lg, bottom = Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Text("How to play", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
        Eyebrow("RULES")
        copy.rules.forEachIndexed { index, rule -> NumberedRule(index + 1, rule, accent) }
        if (copy.controls.isNotEmpty()) {
            Eyebrow("CONTROLS", Modifier.padding(top = Spacing.sm))
            copy.controls.forEach { Text(it, style = MaterialTheme.typography.bodyMedium) }
        }
        PrimaryButton(text = "Got it", onClick = onGotIt, modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm).testTag("rules.got_it"))
        AppTextButton(text = "Replay the tutorial", onClick = onReplay, modifier = Modifier.align(Alignment.CenterHorizontally).testTag("rules.replay"))
    }
}

/** Each game's sheet body alone: ModalBottomSheet draws in its own window, which previews skip. */
@Composable
private fun RulesSheetBodyPreview(id: String, accent: AccentSlot, glyph: ImageVector) {
    PuzzleBoxTheme {
        RulesSheetBody(checkNotNull(rulesSheetCopy(GameId(id))), GameBrand(accent, glyph), onGotIt = {}, onReplay = {})
    }
}

@PuzzleBoxPreviews
@Composable
private fun RulesSheetZipPreview() = RulesSheetBodyPreview("zip", AccentSlot.Blue, GameGlyphs.Zip)

@PuzzleBoxPreviews
@Composable
private fun RulesSheetTangoPreview() = RulesSheetBodyPreview("tango", AccentSlot.Orange, GameGlyphs.Tango)

@PuzzleBoxPreviews
@Composable
private fun RulesSheetQueensPreview() = RulesSheetBodyPreview("queens", AccentSlot.Violet, GameGlyphs.Queens)

@PuzzleBoxPreviews
@Composable
private fun RulesSheetStrandsPreview() = RulesSheetBodyPreview("strands", AccentSlot.Pink, GameGlyphs.Strands)

@PuzzleBoxPreviews
@Composable
private fun RulesSheetMiniSudokuPreview() = RulesSheetBodyPreview("minisudoku", AccentSlot.Teal, GameGlyphs.Sudoku)
