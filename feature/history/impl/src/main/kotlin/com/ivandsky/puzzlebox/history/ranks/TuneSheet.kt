package com.ivandsky.puzzlebox.history.ranks

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.ivandsky.puzzlebox.designsystem.AppModalBottomSheet
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxPreviews
import com.ivandsky.puzzlebox.designsystem.SegmentedToggle
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.domain.ranks.RankScope
import com.ivandsky.puzzlebox.history.RanksTuneDefaults
import com.ivandsky.puzzlebox.history.ranks.RanksSelection.Window

/**
 * The tune sheet: who the board ranks ([scope], Global or Friends) and over what span ([window], a day, the
 * week or the month of the picked day). A tap reports through [onScope] / [onWindow] and the sheet stays open,
 * so the board behind it can be watched changing; [onDismiss] closes it. [defaults] is the Defaults block under
 * them ([TuneDefaultsBlock]).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TuneSheet(
    scope: RankScope,
    window: Window,
    onScope: (RankScope) -> Unit,
    onWindow: (Window) -> Unit,
    onDismiss: () -> Unit,
    defaults: RanksTuneDefaults,
    modifier: Modifier = Modifier,
) {
    // The sheet opens whole instead of half way, so the Defaults block is not cut off.
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    AppModalBottomSheet(onDismissRequest = onDismiss, modifier = modifier.testTag("ranks.tune.sheet"), sheetState = state) {
        TuneChoices(scope, window, onScope, onWindow, defaults)
    }
}

@Composable
private fun TuneChoices(
    scope: RankScope,
    window: Window,
    onScope: (RankScope) -> Unit,
    onWindow: (Window) -> Unit,
    defaults: RanksTuneDefaults,
) {
    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(start = Spacing.lg, end = Spacing.lg, bottom = Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Text("Who", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
        SegmentedToggle(
            options = RankScope.entries,
            selected = scope,
            onSelect = onScope,
            label = { it.name },
            tag = { "ranks.tune.scope.${it.name}" },
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            "When",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = Spacing.sm),
        )
        SegmentedToggle(
            options = Window.entries,
            selected = window,
            onSelect = onWindow,
            label = { it.name },
            tag = { "ranks.tune.window.${it.name}" },
            modifier = Modifier.fillMaxWidth(),
        )
        TuneDefaultsBlock(defaults)
    }
}

@PuzzleBoxPreviews
@Composable
private fun TuneChoicesPreview() {
    RanksPreviewHost {
        TuneChoices(RankScope.Friends, Window.Week, onScope = {}, onWindow = {}, defaults = previewTuneDefaults())
    }
}
