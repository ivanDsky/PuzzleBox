package com.ivandsky.puzzlebox.compete.create.lineup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.compete.create.DuelGame
import com.ivandsky.puzzlebox.designsystem.AppModalBottomSheet
import com.ivandsky.puzzlebox.designsystem.FilterPageSwap
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.gameui.PanePolicy
import com.ivandsky.puzzlebox.model.GameId

private const val TILES_PER_ROW = 2
private const val TILES_PER_ROW_TWO_PANE = 1
private val TWO_PANE_MAX_WIDTH = 960.dp
private const val TILES_PANE_WEIGHT = 2f
private const val SEARCH_PANE_WEIGHT = 3f

/**
 * "Add puzzle N" or, from a row, "Change puzzle N": one tile per game for an "Any" puzzle of it, and [exactPuzzles], the
 * slot for picking one exact puzzle, whose chips open from a filter button beside its field. One column on a phone. Where
 * the app splits panes (the single rule in [PanePolicy]) the sheet widens and splits: the tiles on the left, the
 * exact-puzzle search on the right. A tile calls [onPickGame]; the sheet closes through [onDismiss] (a swipe, the scrim) or when the caller drops it.
 *
 * With [solved] (Past you's sheet: how many puzzles of each game the player has solved) a tile says its count and is off
 * at zero, since nothing could be drawn; [exactPuzzles] is then the search over those solved puzzles, split off the same way.
 *
 * With [filterPage] set (FilterSheets on: a chip of the search was tapped) [filterPageContent] takes the place of all of that, in this same sheet, so no second
 * sheet opens over it; [onFilterPageBack] is the system Back while it shows. The tiles and the search keep their state while it does.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AddPuzzleSheet(
    position: Int,
    changing: Boolean,
    games: List<DuelGame>,
    onPickGame: (GameId) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    exactPuzzles: @Composable () -> Unit,
    solved: Map<GameId, Int>? = null,
    filterPage: PickerFilterPage? = null,
    onFilterPageBack: () -> Unit = {},
    filterPageContent: @Composable (PickerFilterPage) -> Unit = {},
) {
    val window = LocalWindowInfo.current.containerSize
    val twoPane = with(LocalDensity.current) { PanePolicy.isTwoPane(window.width.toDp(), window.height.toDp()) }
    AppModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = modifier.testTag("duel.lineup.sheet"),
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = twoPane),
        sheetMaxWidth = if (twoPane) TWO_PANE_MAX_WIDTH else BottomSheetDefaults.SheetMaxWidth,
    ) {
        FilterPageSwap(page = filterPage, onBack = onFilterPageBack, pageContent = filterPageContent) {
            Column(
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(start = Spacing.lg, end = Spacing.lg, bottom = Spacing.lg),
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                Text(
                    text = if (changing) "Change puzzle $position" else "Add puzzle $position",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (twoPane) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.lg)) {
                        Column(Modifier.weight(TILES_PANE_WEIGHT)) { GameTiles(games, solved, TILES_PER_ROW_TWO_PANE, onPickGame) }
                        Column(Modifier.weight(SEARCH_PANE_WEIGHT)) { exactPuzzles() }
                    }
                } else {
                    GameTiles(games, solved, TILES_PER_ROW, onPickGame)
                    exactPuzzles()
                }
            }
        }
    }
}
