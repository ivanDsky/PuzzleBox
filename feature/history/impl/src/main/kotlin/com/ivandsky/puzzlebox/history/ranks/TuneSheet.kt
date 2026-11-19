package com.ivandsky.puzzlebox.history.ranks

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.ivandsky.puzzlebox.designsystem.AppModalBottomSheet
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxPreviews
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.history.RanksTuneDefaults

/**
 * The tune sheet: the Defaults block ([TuneDefaultsBlock], fed by [defaults]) alone, since the filter chips own Who and When.
 * [onDismiss] closes it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TuneSheet(defaults: RanksTuneDefaults, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    // The sheet opens whole instead of half way, so the Defaults block is not cut off.
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    AppModalBottomSheet(onDismissRequest = onDismiss, modifier = modifier.testTag("ranks.tune.sheet"), sheetState = state) {
        TuneChoices(defaults)
    }
}

@Composable
private fun TuneChoices(defaults: RanksTuneDefaults) {
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(start = Spacing.lg, end = Spacing.lg, bottom = Spacing.lg)) {
        TuneDefaultsBlock(defaults)
    }
}

@PuzzleBoxPreviews
@Composable
private fun TuneChoicesPreview() {
    RanksPreviewHost { TuneChoices(previewTuneDefaults()) }
}
